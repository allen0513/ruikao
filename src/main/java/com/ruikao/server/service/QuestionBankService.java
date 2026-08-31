package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.QuestionDTO;
import com.ruikao.pojo.dto.QuestionPageQueryDTO;
import com.ruikao.pojo.entity.QuestionBank;
import com.ruikao.pojo.vo.QuestionImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionBankService {

    PageResult<QuestionBank> pageQuery(QuestionPageQueryDTO dto);

    void add(QuestionDTO dto);

    void update(QuestionDTO dto);

    void delete(Long id);

    QuestionBank getDetail(Long id);

    /** 题目关联的知识点ID列表（表单回显用） */
    List<Long> getKnowledgePointIds(Long questionId);

    /** Excel 批量导入题库，返回导入结果（成功条数 + 逐行错误） */
    QuestionImportResultVO importExcel(MultipartFile file);
}
