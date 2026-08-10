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
import exception.BusinessException;
import response.ResponseDto;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
public class FileController {

    @Value("${product.image.dir:./product-images}")
    private String imageDir;

    /**
     * 上传商品图片：校验非空与扩展名后落盘，返回可访问的相对路径。
     */
    @PostMapping("/product/image/upload")
    public ResponseDto<String> upload(@RequestParam("file") MultipartFile file) {
        String ext = validateAndGetExt(file);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        File dir = ensureImageDir();
        try {
            file.transferTo(new File(dir, fileName));
        } catch (IOException e) {
            throw new BusinessException("图片保存失败：" + e.getMessage(), e);
        }
        return ResponseDto.success("/product/image/" + fileName);
    }

    /**
     * 校验上传文件非空且为受支持的图片格式，返回小写扩展名（含点）。不合法抛 BusinessException。
     */
    private String validateAndGetExt(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(png|jpg|jpeg|gif|webp)")) {
            throw new BusinessException("仅支持 png/jpg/jpeg/gif/webp 格式");
        }
        return ext;
    }

    /**
     * 确保图片目录存在，创建失败抛 BusinessException。
     */
    private File ensureImageDir() {
        File dir = new File(imageDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("图片目录创建失败");
        }
        return dir;
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
        MediaType type;
        String name = fileName.toLowerCase();
        if (name.endsWith(".png")) {
            type = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".gif")) {
            type = MediaType.IMAGE_GIF;
        } else if (name.endsWith(".webp")) {
            type = MediaType.parseMediaType("image/webp");
        } else {
            type = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .body(new FileSystemResource(file));
    }
}
