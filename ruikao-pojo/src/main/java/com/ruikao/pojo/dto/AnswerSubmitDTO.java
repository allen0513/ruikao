package com.ruikao.pojo.dto;

import lombok.Data;

@Data
public class AnswerSubmitDTO {
    private Long recordId;
    private Long questionId;
    private String answerContent;
}
