package com.ruikao.server.service;

import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;

import java.util.List;

public interface ExamAnswerService {

    /** 保存答案（studentId 用于归属校验） */
    void save(AnswerSubmitDTO dto, Long studentId);

    /** 查询答卷答案（studentId 用于归属校验） */
    List<ExamAnswer> getByRecordId(Long recordId, Long studentId);
}
