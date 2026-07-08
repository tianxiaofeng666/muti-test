package com.example.springbootgit.controller;

import com.example.springbootgit.service.S3Service;
import com.example.springbootgit.utils.FileTypeValidateUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传/下载接口。
 */
@RestController
@RequestMapping("/file")
public class FileController {

    private final S3Service s3Service;

    public FileController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * 文件上传接口。
     * 请求方式：POST multipart/form-data
     * 参数：file（表单字段名）
     * 示例：curl -F "file=@test.png" http://localhost:1212/file/upload
     * <p>
     * 文件获取与格式校验统一由 {@link FileTypeValidateUtil#getAndValidateFile} 完成。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        try {
            MultipartFile file = FileTypeValidateUtil.getAndValidateFile(request);
            Map<String, String> result = s3Service.upload(file);
            body.put("code", 0);
            body.put("msg", "上传成功");
            body.put("data", result);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            body.put("code", 400);
            body.put("msg", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        } catch (IOException e) {
            body.put("code", 500);
            body.put("msg", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    /**
     * 文件下载接口，直接将文件流写到浏览器触发下载，无返回值。
     * 请求方式：GET
     * 参数：objectKey
     * 示例：浏览器访问 http://localhost:1212/file/download?objectKey=xxx.png
     */
    @GetMapping("/download")
    public void download(@RequestParam("objectKey") String objectKey, HttpServletResponse response)
            throws IOException {
        s3Service.download(objectKey, response);
    }

    /**
     * 文件删除接口。
     * 请求方式：DELETE
     * 参数：objectKey
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam("objectKey") String objectKey) {
        s3Service.delete(objectKey);
        Map<String, Object> body = new HashMap<>();
        body.put("code", 0);
        body.put("msg", "删除成功");
        return ResponseEntity.ok(body);
    }
}
