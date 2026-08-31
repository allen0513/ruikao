package com.ruikao.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 考试状态更新参数（状态取值与 ExamConstants 中 STATUS_NOT_STARTED=0 / STATUS_FINISHED=2 对齐）
 */
@Data
public class ExamStatusDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态不合法")
    @Max(value = 2, message = "状态不合法")
    private Integer status;
}