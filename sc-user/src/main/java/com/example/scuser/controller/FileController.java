package com.example.scuser.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import response.ResponseDto;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 图片上传与访问接口。
 */
@RestController
public class FileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileController.class);

    @Value("${user.image.dir:./notice-images}")
    private String imageDir;

    /**
     * 上传图片，返回可访问的相对路径。
     * @param file 图片文件（png/jpg/jpeg/gif/webp）
     */
    @PostMapping("/user/image/upload")
    public ResponseDto<String> upload(@RequestParam("file") MultipartFile file) {
        String error = validateUpload(file);
        if (error != null) {
            return ResponseDto.error(error);
        }
        String ext = extractExt(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        return saveImage(file, fileName);
    }

    /**
     * 上传前校验：非空 + 扩展名白名单。通过返回 null，否则返回错误提示。
     */
    private String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "请选择图片";
        }
        String ext = extractExt(file.getOriginalFilename());
        return ext.matches("\\.(png|jpg|jpeg|gif|webp)") ? null : "仅支持 png/jpg/jpeg/gif/webp 格式";
    }

    /**
     * 取小写扩展名（含点），无扩展名返回空串。
     */
    private static String extractExt(String original) {
        if (original != null && original.contains(".")) {
            return original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        return "";
    }

    /**
     * 落盘保存图片并返回访问路径。
     */
    private ResponseDto<String> saveImage(MultipartFile file, String fileName) {
        File dir = new File(imageDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            return ResponseDto.error("图片目录创建失败");
        }
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            LOGGER.warn("[FileController] 图片保存失败 fileName={}", fileName, e);
            return ResponseDto.error("图片保存失败：" + e.getMessage());
        }
        return ResponseDto.success("/user/image/" + fileName);
    }

    /**
     * 按文件名读取图片，防目录穿越；不存在时返回 404。
     * @param fileName 图片文件名
     */
    @GetMapping("/user/image/{fileName:.+}")
    public ResponseEntity<Resource> view(@PathVariable("fileName") String fileName) {
        if (fileName.contains("..") || fileName.contains(File.separator)) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(new File(imageDir).getAbsoluteFile(), fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(resolveMediaType(fileName.toLowerCase()))
                .body(new FileSystemResource(file));
    }

    /**
     * 根据扩展名解析响应的媒体类型，未知类型按 JPEG 处理。
     */
    private static MediaType resolveMediaType(String name) {
        MediaType type;
        if (name.endsWith(".png")) {
            type = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".gif")) {
            type = MediaType.IMAGE_GIF;
        } else if (name.endsWith(".webp")) {
            type = MediaType.parseMediaType("image/webp");
        } else {
            type = MediaType.IMAGE_JPEG;
        }
        return type;
    }
}
