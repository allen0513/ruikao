package com.ruikao.server.service;

import com.ruikao.pojo.dto.AnswerSubmitDTO;
import com.ruikao.pojo.entity.ExamAnswer;

import java.util.List;

public interface ExamAnswerService {

    void save(AnswerSubmitDTO dto);

    List<ExamAnswer> getByRecordId(Long recordId);
}
