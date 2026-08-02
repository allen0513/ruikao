package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.ExamAssignDTO;
import com.ruikao.pojo.dto.ExamDTO;
import com.ruikao.pojo.dto.ExamPageQueryDTO;
import com.ruikao.pojo.dto.ExamStatusDTO;
import com.ruikao.pojo.vo.ExamVO;
import com.ruikao.pojo.vo.RankVO;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("adminExamController")
@RequestMapping("/api/admin/exam")
@Slf4j
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PostMapping("/page")
    public Result<PageResult<ExamVO>> page(@RequestBody @Valid ExamPageQueryDTO queryDTO) {
        log.info("考试分页查询: {}", queryDTO);
        PageResult<ExamVO> pageResult = examService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @OperLog(module = "考试管理", type = "新增", description = "创建考试:{#examDTO.examName}")
    @PostMapping
    public Result<String> create(@RequestBody @Valid ExamDTO examDTO) {
        log.info("创建考试: {}", examDTO.getExamName());
        examService.add(examDTO);
        return Result.success();
    }

    @OperLog(module = "考试管理", type = "修改", description = "更新考试:{#examDTO.id}")
    @PutMapping
    public Result<String> update(@RequestBody @Valid ExamDTO examDTO) {
        log.info("更新考试, id: {}", examDTO.getId());
        examService.update(examDTO);
        return Result.success();
    }

    @OperLog(module = "考试管理", type = "删除", description = "删除考试:{#id}")
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除考试, id: {}", id);
        examService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<ExamVO> getById(@PathVariable Long id) {
        log.info("获取考试详情, id: {}", id);
        ExamVO examVO = examService.getDetail(id);
        return Result.success(examVO);
    }

    @OperLog(module = "考试管理", type = "修改", description = "更新考试状态:{#examStatusDTO.status}")
    @PutMapping("/status/{id}")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody @Valid ExamStatusDTO examStatusDTO) {
        log.info("更新考试状态, id: {}, status: {}", id, examStatusDTO.getStatus());
        examService.updateStatus(id, examStatusDTO.getStatus());
        return Result.success();
    }

    /**
     * 分配考试给学生
     */
    @OperLog(module = "考试管理", type = "分配", description = "分配考试:{#assignDTO.examId}")
    @PostMapping("/assign")
    public Result<String> assignStudents(@RequestBody @Valid ExamAssignDTO assignDTO) {
        log.info("分配考试, examId={}, studentIds={}", assignDTO.getExamId(), assignDTO.getStudentIds());
        examService.assignStudents(assignDTO.getExamId(), assignDTO.getStudentIds());
        return Result.success();
    }

    /**
     * 获取已分配该考试的学生ID列表
     */
    @GetMapping("/assigned-students/{examId}")
    public Result<List<Long>> getAssignedStudents(@PathVariable Long examId) {
        List<Long> ids = examService.getAssignedStudentIds(examId);
        return Result.success(ids);
    }

    /**
     * 获取考试成绩排行榜（Redis ZSET，前 10 名）
     */
    @GetMapping("/rank/{examId}")
    public Result<List<RankVO>> getExamRank(@PathVariable Long examId) {
        log.info("获取考试成绩排行榜, examId: {}", examId);
        List<RankVO> rankList = examService.getExamRank(examId);
        return Result.success(rankList);
    }
}
