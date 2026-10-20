package com.example.scuser.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import response.ResponseDto;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
public class FileController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileController.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGE_PIXELS = 40_000_000;
    private static final String[] ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "webp"};

    @Value("${user.image.dir:./notice-images}")
    private String imageDir;

    @PostMapping("/user/image/upload")
    public ResponseDto<String> upload(@RequestParam("file") MultipartFile file) {
        String extension = validateUpload(file);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        return saveImage(file, fileName);
    }

    private String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!containsExtension(extension)) {
            throw new IllegalArgumentException("仅支持 png/jpg/jpeg/gif/webp 格式");
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                throw new IllegalArgumentException("图片内容无效或尺寸过大");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("图片内容无效");
        }
        return extension;
    }

    private static boolean containsExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) return true;
        }
        return false;
    }

    private static String extension(String original) {
        if (original == null || !original.contains(".")) return "";
        return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
    }

    private ResponseDto<String> saveImage(MultipartFile file, String fileName) {
        File dir = new File(imageDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            return ResponseDto.error("图片目录创建失败");
        }
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            LOGGER.warn("[FileController] 图片保存失败 fileName={}", fileName, e);
            return ResponseDto.error("图片保存失败");
        }
        return ResponseDto.success("/user/image/" + fileName);
    }

    @GetMapping("/user/image/{fileName:.+}")
    public ResponseEntity<Resource> view(@PathVariable("fileName") String fileName) {
        if (fileName.contains("..") || fileName.contains(File.separator) || !containsExtension(extension(fileName))) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(new File(imageDir).getAbsoluteFile(), fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .contentType(resolveMediaType(extension(fileName)))
                .body(new FileSystemResource(file));
    }

    private static MediaType resolveMediaType(String extension) {
        if ("png".equals(extension)) return MediaType.IMAGE_PNG;
        if ("gif".equals(extension)) return MediaType.IMAGE_GIF;
        if ("webp".equals(extension)) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
