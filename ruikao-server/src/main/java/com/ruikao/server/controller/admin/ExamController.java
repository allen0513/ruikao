package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.ExamAssignDTO;
import com.ruikao.pojo.dto.ExamDTO;
import com.ruikao.pojo.dto.ExamPageQueryDTO;
import com.ruikao.pojo.vo.ExamVO;
import com.ruikao.pojo.vo.RankVO;
import com.ruikao.server.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("adminExamController")
@RequestMapping("/api/admin/exam")
@Slf4j
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    @PostMapping("/page")
    public Result<PageResult<ExamVO>> page(@RequestBody ExamPageQueryDTO queryDTO) {
        log.info("考试分页查询: {}", queryDTO);
        PageResult<ExamVO> pageResult = examService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<String> create(@RequestBody ExamDTO examDTO) {
        log.info("创建考试: {}", examDTO.getExamName());
        examService.add(examDTO);
        return Result.success();
    }

    @PutMapping
    public Result<String> update(@RequestBody ExamDTO examDTO) {
        log.info("更新考试, id: {}", examDTO.getId());
        examService.update(examDTO);
        return Result.success();
    }

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

    @PutMapping("/status/{id}")
    public Result<String> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        log.info("更新考试状态, id: {}, status: {}", id, status);
        examService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 分配考试给学生
     */
    @PostMapping("/assign")
    public Result<String> assignStudents(@RequestBody ExamAssignDTO assignDTO) {
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
