package com.example.scproduct.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import exception.BusinessException;
import response.ResponseDto;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
public class FileController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGE_PIXELS = 40_000_000;

    @Value("${product.image.dir:./product-images}")
    private String imageDir;

    @PostMapping("/product/image/upload")
    public ResponseDto<String> upload(@RequestParam("file") MultipartFile file) {
        String extension = validateAndGetExt(file);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        File dir = ensureImageDir();
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            throw new BusinessException("图片保存失败", e);
        }
        return ResponseDto.success("/product/image/" + fileName);
    }

    private String validateAndGetExt(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择图片");
        if (file.getSize() > MAX_FILE_SIZE) throw new BusinessException("图片不能超过 10MB");
        String extension = extension(file.getOriginalFilename());
        if (!isAllowed(extension)) throw new BusinessException("仅支持 png/jpg/jpeg/gif/webp 格式");
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                throw new BusinessException("图片内容无效或尺寸过大");
            }
        } catch (IOException e) {
            throw new BusinessException("图片内容无效", e);
        }
        return extension;
    }

    private static String extension(String original) {
        if (original == null || !original.contains(".")) return "";
        return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
    }

    private static boolean isAllowed(String extension) {
        return "png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension)
                || "gif".equals(extension) || "webp".equals(extension);
    }

    private File ensureImageDir() {
        File dir = new File(imageDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) throw new BusinessException("图片目录创建失败");
        return dir;
    }

    @GetMapping("/product/image/{fileName:.+}")
    public ResponseEntity<Resource> view(@PathVariable("fileName") String fileName) {
        String extension = extension(fileName);
        if (fileName.contains("..") || fileName.contains(File.separator) || !isAllowed(extension)) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(new File(imageDir).getAbsoluteFile(), fileName);
        if (!file.exists() || !file.isFile()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .contentType(resolveMediaType(extension))
                .body(new FileSystemResource(file));
    }

    private static MediaType resolveMediaType(String extension) {
        if ("png".equals(extension)) return MediaType.IMAGE_PNG;
        if ("gif".equals(extension)) return MediaType.IMAGE_GIF;
        if ("webp".equals(extension)) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
