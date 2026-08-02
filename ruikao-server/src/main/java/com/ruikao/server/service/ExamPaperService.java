package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.PaperDTO;
import com.ruikao.pojo.dto.PaperPageQueryDTO;
import com.ruikao.pojo.entity.ExamPaper;
import com.ruikao.pojo.vo.QuestionVO;

import java.util.List;

public interface ExamPaperService {

    PageResult<ExamPaper> pageQuery(PaperPageQueryDTO dto);

    void add(PaperDTO dto);

    void update(PaperDTO dto);

    void delete(Long id);

    ExamPaper getDetail(Long id);

    List<QuestionVO> getPaperQuestions(Long paperId);

    void publish(Long id);
}
