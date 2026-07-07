package com.example.springbootgit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * S3 文件上传/下载服务。
 * <p>
 * 上传时进行双重格式校验：
 * 1. 扩展名白名单校验；
 * 2. 文件头魔数（Magic Number）校验，防止改扩展名绕过。
 */
@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${s3.bucket}")
    private String bucket;

    @Value("${s3.allowed-extensions}")
    private String allowedExtensions;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * 上传文件。
     *
     * @param file 前端上传的文件
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

        // 1. 扩展名白名单校验
        String extension = getExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("不支持的文件格式: " + extension
                    + "，仅允许: " + allowedExtensions);
        }

        // 2. 文件头魔数校验
        String detectedType = detectFileType(file.getBytes());
        if (detectedType == null || !isMagicNumberAllowed(detectedType, extension)) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配，疑似伪造文件类型");
        }

        // 生成唯一对象 key，避免覆盖同名文件
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
     * 下载文件，返回输入流与响应元信息。
     *
     * @param objectKey S3 上的对象 key
     */
    public DownloadResult download(String objectKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getRequest);
        GetObjectResponse objectResponse = response.response();
        String contentType = objectResponse.contentType() != null
                ? objectResponse.contentType() : "application/octet-stream";
        long contentLength = objectResponse.contentLength() != null
                ? objectResponse.contentLength() : -1L;

        // 从元信息或 key 中推导文件名
        String filename = objectResponse.metadata() != null
                ? objectResponse.metadata().get("original-filename") : null;
        if (filename == null || filename.isEmpty()) {
            filename = objectKey.contains("/") ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : objectKey;
        }

        return new DownloadResult(response, contentType, contentLength, filename);
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

    // ===== 格式校验辅助方法 =====

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension) {
        Set<String> allowed = new HashSet<>();
        for (String ext : allowedExtensions.split(",")) {
            allowed.add(ext.trim().toLowerCase());
        }
        return allowed.contains(extension);
    }

    /**
     * 通过文件头魔数识别真实文件类型。
     */
    private String detectFileType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return "unknown";
        }
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) {
            return "png";
        }
        // GIF: 47 49 46 38
        if ((bytes[0] & 0xFF) == 0x47 && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x38) {
            return "gif";
        }
        // BMP: 42 4D
        if ((bytes[0] & 0xFF) == 0x42 && (bytes[1] & 0xFF) == 0x4D) {
            return "bmp";
        }
        // PDF: 25 50 44 46
        if ((bytes[0] & 0xFF) == 0x25 && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x44 && (bytes[3] & 0xFF) == 0x46) {
            return "pdf";
        }
        // ZIP / docx / xlsx / pptx（均为 PK 开头）: 50 4B 03 04
        if ((bytes[0] & 0xFF) == 0x50 && (bytes[1] & 0xFF) == 0x4B
                && (bytes[2] & 0xFF) == 0x03 && (bytes[3] & 0xFF) == 0x04) {
            return "zip";
        }
        // RAR: 52 61 72 21
        if ((bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x61
                && (bytes[2] & 0xFF) == 0x72 && (bytes[3] & 0xFF) == 0x21) {
            return "rar";
        }
        // 7z: 37 7A BC AF 27 1C
        if (bytes.length >= 6 && (bytes[0] & 0xFF) == 0x37 && (bytes[1] & 0xFF) == 0x7A
                && (bytes[2] & 0xFF) == 0xBC && (bytes[3] & 0xFF) == 0xAF
                && (bytes[4] & 0xFF) == 0x27 && (bytes[5] & 0xFF) == 0x1C) {
            return "7z";
        }
        // 纯文本类（无固定魔数）放宽为 unknown，由扩展名校验把关
        return "unknown";
    }

    /**
     * 校验魔数识别结果与扩展名是否匹配。
     * Office 文档（docx/xlsx/pptx）本质是 zip，统一放行。
     * 纯文本类无法通过魔数识别，仅依赖扩展名校验。
     */
    private boolean isMagicNumberAllowed(String detectedType, String extension) {
        if ("unknown".equals(detectedType)) {
            // txt 等无固定魔数的类型放行（扩展名已校验）
            return Arrays.asList("txt").contains(extension);
        }
        if ("zip".equals(detectedType)) {
            return Arrays.asList("zip", "docx", "xlsx", "pptx").contains(extension);
        }
        if ("jpg".equals(detectedType)) {
            return Arrays.asList("jpg", "jpeg").contains(extension);
        }
        return detectedType.equals(extension);
    }

    /**
     * 下载结果封装。
     */
    public static class DownloadResult {
        private final InputStream inputStream;
        private final String contentType;
        private final long contentLength;
        private final String filename;

        public DownloadResult(InputStream inputStream, String contentType, long contentLength, String filename) {
            this.inputStream = inputStream;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.filename = filename;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public String getContentType() {
            return contentType;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getFilename() {
            return filename;
        }
    }
}
