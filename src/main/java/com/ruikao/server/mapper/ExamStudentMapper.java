package com.ruikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruikao.pojo.entity.ExamStudent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ExamStudentMapper extends BaseMapper<ExamStudent> {

    @Select("SELECT student_id FROM exam_student WHERE exam_id = #{examId}")
    List<Long> getStudentIdsByExamId(@Param("examId") Long examId);

    @Select("SELECT exam_id FROM exam_student WHERE student_id = #{studentId}")
    List<Long> getExamIdsByStudentId(@Param("studentId") Long studentId);
}
