package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class QuestionDTO {

    private Long id;

    @NotNull(message = "题型不能为空")
    private Integer questionType;

    @NotBlank(message = "题目内容不能为空")
    private String questionContent;

    private String options;
    private String answer;

    /** 答案解析 */
    private String analysis;

    private BigDecimal score;
    private Integer difficulty;

    /** 所属科目ID（subject.id） */
    private Long subjectId;

    /** 关联知识点ID列表（多选，随题目事务维护 question_knowledge_point） */
    private List<Long> knowledgePointIds;
}