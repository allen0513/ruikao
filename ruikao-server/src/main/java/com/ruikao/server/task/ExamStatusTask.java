package com.ruikao.server.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.server.mapper.ExamMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@Slf4j
public class ExamStatusTask {

    @Autowired
    private ExamMapper examMapper;

    @Scheduled(fixedRate = 60000)
    public void updateExamStatus() {
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();

            // 将未开始的考试更新为进行中
            LambdaUpdateWrapper<Exam> startWrapper = new LambdaUpdateWrapper<>();
            startWrapper.eq(Exam::getStatus, ExamConstants.STATUS_NOT_STARTED)
                    .eq(Exam::getExamDate, today.toString())
                    .le(Exam::getStartTime, now.toString())
                    .set(Exam::getStatus, ExamConstants.STATUS_IN_PROGRESS);
            int started = examMapper.update(null, startWrapper);

            // 将进行中的考试更新为已结束
            LambdaUpdateWrapper<Exam> endWrapper = new LambdaUpdateWrapper<>();
            endWrapper.eq(Exam::getStatus, ExamConstants.STATUS_IN_PROGRESS)
                    .eq(Exam::getExamDate, today.toString())
                    .le(Exam::getEndTime, now.toString())
                    .set(Exam::getStatus, ExamConstants.STATUS_FINISHED);
            int ended = examMapper.update(null, endWrapper);

            if (started > 0 || ended > 0) {
                log.info("考试状态更新: {}场开始, {}场结束", started, ended);
            }
        } catch (Exception e) {
            log.error("考试状态更新失败", e);
        }
    }
}
