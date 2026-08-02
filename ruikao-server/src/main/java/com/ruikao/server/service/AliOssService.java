package com.ruikao.server.service;

import org.springframework.web.multipart.MultipartFile;

public interface AliOssService {

    /**
     * 上传文件到 OSS，返回完整可访问 URL
     * @param file 上传的文件
     * @param dir 目录前缀，如 avatar、question，用于按业务归类
     */
    String upload(MultipartFile file, String dir);

    /**
     * 上传字节数组到 OSS，返回完整可访问 URL
     * @param bytes 文件内容
     * @param dir 目录前缀
     * @param ext 扩展名，如 .jpg（含点）
     */
    String upload(byte[] bytes, String dir, String ext);
}