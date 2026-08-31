package com.ruikao.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ExamAssignDTO {

    @NotNull(message = "考试ID不能为空")
    private Long examId;

    @NotEmpty(message = "学生列表不能为空")
    private List<Long> studentIds;
}