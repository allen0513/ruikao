package com.ruikao.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.pojo.entity.ExamRecord;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /** 已出分记录的平均分（score 非空） */
    @Select("SELECT AVG(score) FROM exam_record WHERE score IS NOT NULL")
    BigDecimal selectAvgScore();

    /** 已出分记录数 */
    @Select("SELECT COUNT(*) FROM exam_record WHERE score IS NOT NULL")
    Long selectCountWithScore();

    /** 已出分且及格的记录数 */
    @Select("SELECT COUNT(*) FROM exam_record WHERE score IS NOT NULL AND score >= " + ExamConstants.PASS_LINE)
    Long selectPassCount();
}
