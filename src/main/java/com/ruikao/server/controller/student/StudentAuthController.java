package com.ruikao.server.controller.student;

import com.ruikao.common.constant.ExamConstants;
import com.ruikao.common.constant.JwtClaimsConstant;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.AccountNotFoundException;
import com.ruikao.common.exception.BusinessException;
import com.ruikao.common.exception.PasswordErrorException;
import com.ruikao.common.properties.JwtProperties;
import com.ruikao.common.result.Result;
import com.ruikao.common.utils.JwtUtil;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.pojo.dto.LoginDTO;
import com.ruikao.pojo.entity.Student;
import com.ruikao.pojo.vo.StudentLoginVO;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.security.LoginAttemptService;
import com.ruikao.server.security.TokenBlacklistService;
import com.ruikao.server.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/student/auth")
@Slf4j
public class StudentAuthController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    /**
     * 学号+密码登录（保留，管理后台导入的学生可用）
     */
    @OperLog(module = "登录认证", type = "登录", description = "登录:{#loginDTO.username}")
    @PostMapping("/login")
    public Result<StudentLoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        log.info("学生登录: {}", loginDTO.getUsername());

        // 限流：连续失败 5 次锁定 15 分钟
        loginAttemptService.checkNotLocked(loginDTO.getUsername());

        Student student = studentService.findByStudentNo(loginDTO.getUsername());
        if (student == null) {
            loginAttemptService.recordFailure(loginDTO.getUsername());
            throw new AccountNotFoundException("学号不存在");
        }

        if (!PasswordUtil.matches(loginDTO.getPassword(), student.getPassword())) {
            loginAttemptService.recordFailure(loginDTO.getUsername());
            throw new PasswordErrorException("密码错误");
        }

        // 禁用账号禁止登录
        if (student.getStatus() != ExamConstants.USER_STATUS_ENABLED) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        loginAttemptService.recordSuccess(loginDTO.getUsername());
        // 供操作日志切面记录操作人
        BaseContext.setCurrentUsername(student.getName());

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, student.getId());
        claims.put(JwtClaimsConstant.USERNAME, student.getStudentNo());

        String token = JwtUtil.createJWT(
                jwtProperties.getStudentSecretKey(),
                jwtProperties.getStudentTtl(),
                claims
        );

        StudentLoginVO studentLoginVO = new StudentLoginVO();
        studentLoginVO.setId(student.getId());
        studentLoginVO.setStudentNo(student.getStudentNo());
        studentLoginVO.setName(student.getName());
        studentLoginVO.setAvatar(student.getAvatar());
        studentLoginVO.setToken(token);

        return Result.success(studentLoginVO);
    }

    @OperLog(module = "登录认证", type = "登出", description = "登出")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        Long studentId = BaseContext.getCurrentId();
        log.info("学生登出: {}", studentId);
        // 登出即失效：将当前 token 加入黑名单，剩余有效期内不可再用
        String token = request.getHeader(jwtProperties.getStudentTokenName());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        tokenBlacklistService.blacklist("student", token, jwtProperties.getStudentTtl());
        BaseContext.remove();
        return Result.success();
    }
}
