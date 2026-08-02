package com.ruikao.server.service;

import com.ruikao.common.result.PageResult;
import com.ruikao.pojo.dto.ExamDTO;
import com.ruikao.pojo.dto.ExamPageQueryDTO;
import com.ruikao.pojo.vo.ExamVO;
import com.ruikao.pojo.vo.RankVO;

import java.util.List;

public interface ExamService {

    PageResult<ExamVO> pageQuery(ExamPageQueryDTO dto);

    void add(ExamDTO dto);

    void update(ExamDTO dto);

    void delete(Long id);

    ExamVO getDetail(Long id);

    void updateStatus(Long id, Integer status);

    /**
     * 分配考试给学生
     * @param examId 考试ID
     * @param studentIds 学生ID列表
     */
    void assignStudents(Long examId, List<Long> studentIds);

    /**
     * 获取已分配该考试的学生ID列表
     */
    List<Long> getAssignedStudentIds(Long examId);

    /**
     * 获取考试成绩排行榜（Redis ZSET，前 10 名）
     */
    List<RankVO> getExamRank(Long examId);
}
