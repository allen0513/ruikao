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

@RestController
@RequestMapping("/api/common")
@Slf4j
public class FileController {

    @Autowired
    private AliOssService aliOssService;

    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file,
                                 @RequestParam(defaultValue = "upload") String dir) {
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        String url = aliOssService.upload(file, dir);
        log.info("文件上传成功: {}", url);
        return Result.success(url);
    }
}