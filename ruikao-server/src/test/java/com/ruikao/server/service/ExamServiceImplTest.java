package com.ruikao.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ruikao.pojo.entity.ExamStudent;
import com.ruikao.server.mapper.ExamMapper;
import com.ruikao.server.mapper.ExamPaperMapper;
import com.ruikao.server.mapper.ExamRecordMapper;
import com.ruikao.server.mapper.ExamStudentMapper;
import com.ruikao.server.mapper.StudentMapper;
import com.ruikao.server.mapper.SysUserMapper;
import com.ruikao.server.service.impl.ExamServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试分配测试：学生 ID 去重（防唯一键冲突）、旧/新分配名单按学生 keyed 驱逐缓存
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceImplTest {

    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamPaperMapper examPaperMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private ExamStudentMapper examStudentMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private ExamRecordMapper examRecordMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;

    @InjectMocks
    private ExamServiceImpl examServiceImpl;

    /** 纯 Mockito 无 Spring 上下文，手动初始化被分配实体的 TableInfo（Lambda 包装器需要） */
    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamStudent.class);
    }

    @Test
    void assignStudents_deduplicatesAndEvictsOldAndNewStudents() {
        // 原分配学生 3，新名单含重复 4/4/5/5
        when(examStudentMapper.getStudentIdsByExamId(1L)).thenReturn(List.of(3L));
        when(cacheManager.getCache("studentExamList")).thenReturn(cache);

        examServiceImpl.assignStudents(1L, List.of(3L, 4L, 4L, 5L, 5L));

        // 去重后只插入 3 条，且为学生 3/4/5
        ArgumentCaptor<ExamStudent> captor = ArgumentCaptor.forClass(ExamStudent.class);
        verify(examStudentMapper, times(3)).insert(captor.capture());
        Set<Long> inserted = captor.getAllValues().stream()
                .map(ExamStudent::getStudentId).collect(Collectors.toSet());
        assertEquals(Set.of(3L, 4L, 5L), inserted);

        // 驱逐范围 = 旧分配 {3} ∪ 新名单 {3,4,5}；3 在删除前/插入后各驱逐一次
        verify(cache, times(2)).evict(3L);
        verify(cache).evict(4L);
        verify(cache).evict(5L);
        verify(cache, times(4)).evict(anyLong());
    }

    @Test
    void assignStudents_nullList_clearsOldAssignmentsOnly() {
        when(examStudentMapper.getStudentIdsByExamId(1L)).thenReturn(List.of(3L));
        when(cacheManager.getCache("studentExamList")).thenReturn(cache);

        examServiceImpl.assignStudents(1L, null);

        // 旧分配被清空且无新插入；旧名单 3 在删除前/插入后各驱逐一次
        verify(examStudentMapper).delete(any());
        verify(examStudentMapper, times(0)).insert(any(ExamStudent.class));
        verify(cache, times(2)).evict(3L);
    }
}