package com.ruikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruikao.pojo.entity.Exam;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ExamMapper extends BaseMapper<Exam> {

    /** 按月份统计考试数量（SQL 聚合，避免全表加载到内存） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m') AS month, COUNT(*) AS cnt " +
            "FROM exam WHERE create_time IS NOT NULL " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m')")
    List<Map<String, Object>> selectExamTrendByMonth();
}
