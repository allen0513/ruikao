package com.ruikao.server.service;

import org.springframework.web.multipart.MultipartFile;

public interface AliOssService {

    /**
     * 上传文件到 OSS，返回完整可访问 URL
     * @param file 上传的文件
     * @param dir 目录前缀，如 avatar、question，用于按业务归类
     */
    String upload(MultipartFile file, String dir);
}