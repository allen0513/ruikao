package com.ruikao.pojo.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExamAssignDTO {
    private Long examId;
    private List<Long> studentIds;
}
