package com.ruikao.server.controller.student;

import com.ruikao.common.context.BaseContext;
import com.ruikao.common.result.Result;
import com.ruikao.server.service.StudentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/student/profile")
@Slf4j
public class StudentProfileController {

    @Autowired
    private StudentService studentService;

    /** 更新当前登录学生的头像 */
    @PutMapping("/avatar")
    public Result<String> updateAvatar(@RequestBody Map<String, String> body) {
        Long studentId = BaseContext.getCurrentId();
        log.info("更新学生头像, id: {}", studentId);
        studentService.updateAvatar(studentId, body.get("avatar"));
        return Result.success();
    }
}