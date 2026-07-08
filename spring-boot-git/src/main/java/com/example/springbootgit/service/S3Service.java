package com.example.springbootgit.service;

import com.example.springbootgit.utils.FileTypeValidateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S3 文件上传/下载服务。
 * <p>
 * 文件格式校验已抽离到 {@link FileTypeValidateUtil}，本类只负责与 S3 的交互。
 */
@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucket;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * 上传文件到 S3。
     * <p>
     * 注意：文件格式校验应由调用方通过 {@link FileTypeValidateUtil#getAndValidateFile} 完成后再传入。
     *
     * @param file 已校验的上传文件
     * @return 上传后的对象 key 及原始文件名
     */
    public Map<String, String> upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 生成唯一对象 key，避免覆盖同名文件
        String extension = FileTypeValidateUtil.getExtension(originalFilename);
        String objectKey = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));

        Map<String, String> result = new HashMap<>();
        result.put("objectKey", objectKey);
        result.put("originalFilename", originalFilename);
        result.put("size", String.valueOf(file.getSize()));
        return result;
    }

    /**
     * 下载文件，将 S3 对象流直接写入 HttpServletResponse，浏览器直接触发下载。
     * <p>
     * 拉取对象失败（如 objectKey 不存在）时，响应尚未提交，返回 404 JSON 错误信息。
     *
     * @param objectKey S3 上的对象 key
     * @param response  HTTP 响应
     */
    public void download(String objectKey, HttpServletResponse response) throws IOException {
        ResponseInputStream<GetObjectResponse> s3Stream;
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            s3Stream = s3Client.getObject(getRequest);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":404,\"msg\":\"文件不存在或下载失败: "
                    + e.getMessage() + "\"}");
            return;
        }

        try (ResponseInputStream<GetObjectResponse> is = s3Stream) {
            GetObjectResponse objectResponse = is.response();

            String contentType = objectResponse.contentType() != null
                    ? objectResponse.contentType() : "application/octet-stream";
            long contentLength = objectResponse.contentLength() != null
                    ? objectResponse.contentLength() : -1L;

            // 从元信息或 key 推导文件名
            String filename = (objectResponse.metadata() != null)
                    ? objectResponse.metadata().get("original-filename") : null;
            if (filename == null || filename.isEmpty()) {
                filename = objectKey.contains("/")
                        ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : objectKey;
            }

            // 中文文件名编码，兼容主流浏览器
            String encodedFilename;
            try {
                encodedFilename = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            } catch (Exception e) {
                encodedFilename = filename;
            }

            // 设置响应头，浏览器识别为下载行为
            response.setContentType(contentType);
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename);

            // 将 S3 输入流直接写到响应输出流
            StreamUtils.copy(is, response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    /**
     * 删除文件。
     */
    public void delete(String objectKey) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.deleteObject(deleteRequest);
    }
}
