package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.QuestionBank;

public interface QuestionBankService {

    PageResult<QuestionBank> pageQuery(QuestionPageQueryDTO dto);

    void add(QuestionDTO dto);

    void update(QuestionDTO dto);

    void delete(Long id);

    QuestionBank getDetail(Long id);
}
