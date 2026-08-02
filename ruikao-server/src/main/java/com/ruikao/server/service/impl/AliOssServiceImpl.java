package com.ruikao.server.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.ruikao.common.properties.AliOssProperties;
import com.ruikao.server.service.AliOssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class AliOssServiceImpl implements AliOssService {

    private final AliOssProperties aliOssProperties;

    public AliOssServiceImpl(AliOssProperties aliOssProperties) {
        this.aliOssProperties = aliOssProperties;
    }

    @Override
    public String upload(MultipartFile file, String dir) {
        String ext = "";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        try (InputStream inputStream = file.getInputStream()) {
            return upload(inputStream, dir, ext);
        } catch (IOException e) {
            log.error("读取上传文件流失败", e);
            throw new RuntimeException("文件上传失败");
        }
    }

    @Override
    public String upload(byte[] bytes, String dir, String ext) {
        return upload(new java.io.ByteArrayInputStream(bytes), dir, ext);
    }

    private String upload(InputStream inputStream, String dir, String ext) {
        // 对象名：avatar/20260802/uuid.jpg，按日期归档便于管理
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String objectName = dir + "/" + date + "/" + UUID.randomUUID() + ext;

        OSS ossClient = new OSSClientBuilder()
                .build(aliOssProperties.getEndpoint(),
                        aliOssProperties.getAccessKeyId(),
                        aliOssProperties.getAccessKeySecret());
        try {
            ossClient.putObject(aliOssProperties.getBucketName(), objectName, inputStream);
            String url = "https://" + aliOssProperties.getBucketName() + "."
                    + aliOssProperties.getEndpoint() + "/" + objectName;
            log.info("文件上传 OSS 成功: {}", url);
            return url;
        } finally {
            ossClient.shutdown();
        }
    }
}