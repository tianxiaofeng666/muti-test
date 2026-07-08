package com.example.springbootgit.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 上传文件类型校验工具类。
 * <p>
 * 统一从 HttpServletRequest 获取上传文件，并进行：
 * 1. 扩展名白名单校验；
 * 2. 文件头魔数（Magic Number）校验，防止改扩展名绕过。
 * <p>
 * 校验失败抛出 {@link IllegalArgumentException}，由调用方捕获处理。
 */
public class FileTypeValidateUtil {

    private static final Logger log = LoggerFactory.getLogger(FileTypeValidateUtil.class);

    /**
     * 默认允许上传的文件扩展名（yaml 不再配置，统一在此维护）。
     */
    private static final List<String> DEFAULT_ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "pdf",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "zip", "rar", "7z"
    );

    private FileTypeValidateUtil() {
    }

    /**
     * 获取文件扩展名（不含点，小写）。
     *
     * @param filename 文件名
     * @return 扩展名；无扩展名返回空串
     */
    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 校验上传文件的扩展名是否在白名单内。
     *
     * @param file         上传的文件
     * @param allowExtends 允许的扩展名集合
     * @return true=合法，false=非法
     */
    public static boolean validateFile(MultipartFile file, List<String> allowExtends) {
        if (file == null || file.isEmpty()) {
            log.error("文件为空，校验失败");
            return false;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            log.error("文件名格式错误，无扩展名：{}", originalFilename);
            return false;
        }
        String fileExt = getExtension(originalFilename);
        return allowExtends.contains(fileExt);
    }

    /**
     * 校验文件头魔数与扩展名是否匹配，防止伪造文件类型。
     *
     * @param file 上传的文件
     * @return true=合法，false=伪造或无法识别
     */
    public static boolean validateFileContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        try {
            byte[] bytes = file.getBytes();
            String detected = detectFileType(bytes);
            String ext = getExtension(file.getOriginalFilename());
            return isMagicNumberAllowed(detected, ext);
        } catch (IOException e) {
            log.error("读取文件内容失败，魔数校验失败", e);
            return false;
        }
    }

    // ===== 快捷校验方法 =====

    public static boolean isImageFile(MultipartFile file) {
        return validateFile(file, Arrays.asList("jpg", "jpeg", "png", "gif", "bmp"));
    }

    public static boolean isPdfFile(MultipartFile file) {
        return validateFile(file, Arrays.asList("pdf"));
    }

    public static boolean isExcelFile(MultipartFile file) {
        return validateFile(file, Arrays.asList("xlsx", "xls"));
    }

    public static boolean isWordFile(MultipartFile file) {
        return validateFile(file, Arrays.asList("docx", "doc"));
    }

    // ===== 从 Request 获取并校验 =====

    /**
     * 从 HttpServletRequest 获取名为 "file" 的上传文件，使用默认扩展名白名单校验。
     *
     * @param request HttpServletRequest
     * @return 校验通过的 MultipartFile
     * @throws IllegalArgumentException 文件为空、扩展名非法或内容伪造时抛出
     */
    public static MultipartFile getAndValidateFile(HttpServletRequest request) {
        return getAndValidateFile(request, DEFAULT_ALLOWED_EXTENSIONS);
    }

    /**
     * 从 HttpServletRequest 获取名为 "file" 的上传文件，使用指定扩展名白名单校验。
     *
     * @param request      HttpServletRequest
     * @param allowExtends 允许的扩展名集合
     * @return 校验通过的 MultipartFile
     * @throws IllegalArgumentException 文件为空、扩展名非法或内容伪造时抛出
     */
    public static MultipartFile getAndValidateFile(HttpServletRequest request, List<String> allowExtends) {
        MultipartHttpServletRequest multipartRequest = WebUtils.getNativeRequest(
                request, MultipartHttpServletRequest.class);
        if (multipartRequest == null) {
            throw new IllegalArgumentException("当前请求非文件上传请求");
        }
        MultipartFile file = multipartRequest.getFile("file");
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 1. 扩展名白名单校验
        if (!validateFile(file, allowExtends)) {
            String ext = getExtension(file.getOriginalFilename());
            throw new IllegalArgumentException("不支持的文件格式: " + ext
                    + "，仅允许: " + allowExtends);
        }

        // 2. 文件头魔数校验
        if (!validateFileContent(file)) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配，疑似伪造文件类型");
        }

        return file;
    }

    // ===== 魔数识别（私有） =====

    /**
     * 通过文件头魔数识别真实文件类型。
     */
    private static String detectFileType(byte[] bytes) {
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
    private static boolean isMagicNumberAllowed(String detectedType, String extension) {
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
}
