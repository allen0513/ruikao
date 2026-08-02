package com.ruikao.server.controller.admin;

import com.ruikao.common.result.PageResult;
import com.ruikao.common.result.Result;
import com.ruikao.pojo.dto.StudentPageQueryDTO;
import com.ruikao.pojo.entity.Student;
import com.ruikao.server.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/student")
@Slf4j
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @PostMapping("/page")
    public Result<PageResult<Student>> page(@RequestBody StudentPageQueryDTO queryDTO) {
        log.info("学生分页查询: {}", queryDTO);
        PageResult<Student> pageResult = studentService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<String> create(@RequestBody Student student) {
        log.info("创建学生: {}", student.getName());
        studentService.add(student);
        return Result.success();
    }

    @PutMapping
    public Result<String> update(@RequestBody Student student) {
        log.info("更新学生, id: {}", student.getId());
        studentService.update(student);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除学生, id: {}", id);
        studentService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<Student> getById(@PathVariable Long id) {
        log.info("获取学生详情, id: {}", id);
        Student student = studentService.getById(id);
        return Result.success(student);
    }
}
