package com.ruikao.pojo.dto;

import lombok.Data;

/**
 * WebSocket消息DTO —— 来单/阅卷提醒消息结构
 */
@Data
public class WebSocketMessageDTO {

    /** 消息类型: 1=待阅卷提醒, 2=预留 */
    private Integer type;

    /** 考试记录ID */
    private Long recordId;

    /** 消息内容 */
    private String content;

    /** 考试名称 */
    private String examName;

    /** 学生姓名 */
    private String studentName;
}