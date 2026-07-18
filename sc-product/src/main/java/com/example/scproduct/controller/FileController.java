package com.example.scproduct.controller;

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

@RestController
public class FileController {

    @Value("${product.image.dir:./product-images}")
    private String imageDir;

    @PostMapping("/product/image/upload")
    public ResponseDto<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseDto.error("请选择图片");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(png|jpg|jpeg|gif|webp)")) {
            return ResponseDto.error("仅支持 png/jpg/jpeg/gif/webp 格式");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        File dir = new File(imageDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            return ResponseDto.error("图片目录创建失败");
        }
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            return ResponseDto.error("图片保存失败：" + e.getMessage());
        }
        return ResponseDto.success("/product/image/" + fileName);
    }

    @GetMapping("/product/image/{fileName:.+}")
    public ResponseEntity<Resource> view(@PathVariable("fileName") String fileName) {
        if (fileName.contains("..") || fileName.contains(File.separator)) {
            return ResponseEntity.badRequest().build();
        }
        File file = new File(new File(imageDir).getAbsoluteFile(), fileName);
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = MediaType.IMAGE_JPEG;
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) type = MediaType.IMAGE_PNG;
        else if (name.endsWith(".gif")) type = MediaType.IMAGE_GIF;
        else if (name.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        return ResponseEntity.ok()
                .contentType(type)
                .body(new FileSystemResource(file));
    }
}
