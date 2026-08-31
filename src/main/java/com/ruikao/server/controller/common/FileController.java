package com.ruikao.server.controller.common;

import com.ruikao.common.result.Result;
import com.ruikao.server.service.AliOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/common")
@Slf4j
public class FileController {

    /** 允许上传的扩展名白名单（svg 有 XSS 向量，不入白名单） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // 图片
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico",
            // 文档
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
            // 音视频
            "mp3", "wav", "mp4"
    );

    @Autowired
    private AliOssService aliOssService;

    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file,
                                 @RequestParam(defaultValue = "upload") String dir) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        // 扩展名白名单校验，防止上传任意可执行/恶意文件
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            log.warn("文件上传被拒绝: 非法扩展名 [{}], 文件名: {}", ext, originalFilename);
            return Result.error("不支持的文件类型");
        }

        String url = aliOssService.upload(file, dir);
        log.info("文件上传成功: {}", url);
        return Result.success(url);
    }
}