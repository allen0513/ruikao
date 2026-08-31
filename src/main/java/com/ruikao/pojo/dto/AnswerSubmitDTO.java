package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerSubmitDTO {

    @NotNull(message = "考试记录ID不能为空")
    private Long recordId;

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    private String answerContent;

    /** 操作题作答附件URL（OSS，随答案保存） */
    private String attachmentUrl;
}