package com.ruikao.server.task;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruikao.pojo.entity.Exam;
import com.ruikao.pojo.entity.ExamRecord;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.service.ExamRecordService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时收卷任务测试：状态无变化时不动缓存、批量收卷推进、单条失败不中断、变化时清理缓存
 */
@ExtendWith(MockitoExtension.class)
class ExamStatusTaskTest {

    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamRecordMapper examRecordMapper;
    @Mock
    private ExamRecordService examRecordService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache studentExamListCache;
    @Mock
    private Cache chartCache;

    @InjectMocks
    private ExamStatusTask examStatusTask;

    /**
     * 纯 Mockito 无 Spring 上下文，LambdaUpdateWrapper.set 依赖 TableInfo 元数据缓存，
     * 手动初始化被测实体（与 Spring 启动时 MybatisPlusAutoConfiguration 干的事等价）
     */
    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Exam.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamRecord.class);
    }

    @Test
    void updateExamStatus_noChanges_skipsCacheEviction() {
        when(examMapper.update(any(), any())).thenReturn(0);
        when(examRecordMapper.selectList(any())).thenReturn(List.of());

        examStatusTask.updateExamStatus();

        // 状态与答卷均无变化时，不触碰缓存
        verify(cacheManager, never()).getCache(anyString());
        verify(examRecordService, never()).forceSubmitBySystem(any(Long.class));
    }

    @Test
    void updateExamStatus_forcesSubmitInBatchesAndClearsCaches() {
        when(examMapper.update(any(), any())).thenReturn(1); // 有考试状态变化

        // 分批查询：第一批 2 条待收卷记录，第二批为空 → 循环终止
        ExamRecord r1 = new ExamRecord();
        r1.setId(10L);
        ExamRecord r2 = new ExamRecord();
        r2.setId(20L);
        when(examRecordMapper.selectList(any())).thenReturn(List.of(r1, r2)).thenReturn(List.of());

        when(cacheManager.getCache("studentExamList")).thenReturn(studentExamListCache);
        when(cacheManager.getCache("chart")).thenReturn(chartCache);

        examStatusTask.updateExamStatus();

        // 两条答卷都走服务层强制收卷（含客观题自动评分）
        verify(examRecordService).forceSubmitBySystem(10L);
        verify(examRecordService).forceSubmitBySystem(20L);
        // 分页推进：第二批以 lastId=20 继续查，直到为空
        verify(examRecordMapper, times(2)).selectList(any());
        // 状态或答卷有变化 → 清理学生端列表与图表缓存
        verify(studentExamListCache).clear();
        verify(chartCache).clear();
    }

    @Test
    void updateExamStatus_singleForceSubmitFailure_continuesBatch() {
        when(examMapper.update(any(), any())).thenReturn(0);

        ExamRecord r1 = new ExamRecord();
        r1.setId(1L);
        ExamRecord r2 = new ExamRecord();
        r2.setId(2L);
        when(examRecordMapper.selectList(any())).thenReturn(List.of(r1, r2)).thenReturn(List.of());

        // 第一条收卷失败（如并发已被学生手动交卷），不应中断后续批次
        doThrow(new RuntimeException("收卷失败")).when(examRecordService).forceSubmitBySystem(1L);

        examStatusTask.updateExamStatus();

        verify(examRecordService).forceSubmitBySystem(2L);
    }
}