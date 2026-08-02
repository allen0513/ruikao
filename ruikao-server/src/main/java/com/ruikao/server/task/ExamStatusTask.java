package com.ruikao.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruikao.common.constant.ExamConstants;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.service.ExamRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExamStatusTask {

    private final ExamMapper examMapper;

    private final ExamRecordMapper examRecordMapper;

    private final ExamRecordService examRecordService;

    private final CacheManager cacheManager;

    @Scheduled(fixedRate = 60000)
    public void updateExamStatus() {
        try {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();

            // 1. 未开始且已过结束时间 → 直接已结束（含历史日期，弥补停机漏跑）
            LambdaUpdateWrapper<Exam> skipWrapper = new LambdaUpdateWrapper<>();
            skipWrapper.eq(Exam::getStatus, ExamConstants.STATUS_NOT_STARTED)
                    .and(w -> w.lt(Exam::getExamDate, today)
                            .or(o -> o.eq(Exam::getExamDate, today).le(Exam::getEndTime, now)))
                    .set(Exam::getStatus, ExamConstants.STATUS_FINISHED);
            int skipped = examMapper.update(null, skipWrapper);

            // 2. 未开始且已到开始时间 → 进行中
            LambdaUpdateWrapper<Exam> startWrapper = new LambdaUpdateWrapper<>();
            startWrapper.eq(Exam::getStatus, ExamConstants.STATUS_NOT_STARTED)
                    .and(w -> w.lt(Exam::getExamDate, today)
                            .or(o -> o.eq(Exam::getExamDate, today).le(Exam::getStartTime, now)))
                    .set(Exam::getStatus, ExamConstants.STATUS_IN_PROGRESS);
            int started = examMapper.update(null, startWrapper);

            // 3. 进行中且已到结束时间 → 已结束
            LambdaUpdateWrapper<Exam> endWrapper = new LambdaUpdateWrapper<>();
            endWrapper.eq(Exam::getStatus, ExamConstants.STATUS_IN_PROGRESS)
                    .and(w -> w.lt(Exam::getExamDate, today)
                            .or(o -> o.eq(Exam::getExamDate, today).le(Exam::getEndTime, now)))
                    .set(Exam::getStatus, ExamConstants.STATUS_FINISHED);
            int ended = examMapper.update(null, endWrapper);

            if (skipped > 0 || started > 0 || ended > 0) {
                log.info("考试状态更新: {}场直接结束, {}场开始, {}场结束", skipped, started, ended);
            }

            // 4. 自动收卷：考试已结束但学生未交卷（status=1）的记录强制收卷
            int autoSubmitted = forceSubmitFinishedExams();
            if (autoSubmitted > 0) {
                log.info("自动收卷完成: 强制交卷 {} 份答卷", autoSubmitted);
            }

            // 状态或答卷有变化时，清理学生端考试列表缓存与图表统计缓存，避免滞留旧状态
            if (skipped > 0 || started > 0 || ended > 0 || autoSubmitted > 0) {
                Cache cache = cacheManager.getCache("studentExamList");
                if (cache != null) {
                    cache.clear();
                }
                Cache chartCache = cacheManager.getCache("chart");
                if (chartCache != null) {
                    chartCache.clear();
                }
            }
        } catch (Exception e) {
            log.error("考试状态更新失败", e);
        }
    }

    /**
     * 对已结束考试中尚未交卷的答卷强制收卷（status 1 → 2，并执行客观题自动评分），
     * 防止学生无限期补交、防止被强制收卷的答卷客观题按 0 分定稿。重复执行幂等。
     * 只查待收卷记录（已结束考试的 status=1 记录），按 id 分批，收卷完成后扫描量自然趋零。
     */
    private int forceSubmitFinishedExams() {
        int total = 0;
        long lastId = 0;
        while (true) {
            List<ExamRecord> pending = examRecordMapper.selectList(new LambdaQueryWrapper<ExamRecord>()
                    .eq(ExamRecord::getStatus, ExamConstants.RECORD_STATUS_EXAMINING)
                    // 子查询常量拼接，无注入风险；限定已结束考试，避免扫描全部记录
                    .inSql(ExamRecord::getExamId,
                            "SELECT id FROM exam WHERE status = " + ExamConstants.STATUS_FINISHED)
                    .gt(ExamRecord::getId, lastId)
                    .orderByAsc(ExamRecord::getId)
                    .last("LIMIT 100"));
            if (pending.isEmpty()) {
                break;
            }
            for (ExamRecord record : pending) {
                try {
                    // 强制收卷走服务层：执行客观题自动评分 + 置为已交卷（幂等，带记录级锁）
                    examRecordService.forceSubmitBySystem(record.getId());
                    total++;
                } catch (Exception e) {
                    log.error("自动收卷失败, recordId={}", record.getId(), e);
                }
            }
            lastId = pending.get(pending.size() - 1).getId();
        }
        return total;
    }
}