package com.ruikao.server.controller.student;

import com.ruikao.common.constant.JwtClaimsConstant;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.AccountNotFoundException;
import com.ruikao.common.exception.PasswordErrorException;
import com.ruikao.common.properties.JwtProperties;
import com.ruikao.common.result.Result;
import com.ruikao.common.utils.JwtUtil;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.pojo.dto.LoginDTO;
import com.ruikao.pojo.dto.WxBindDTO;
import com.ruikao.pojo.dto.WxLoginDTO;
import com.ruikao.pojo.entity.Student;
import com.ruikao.pojo.vo.StudentLoginVO;
import com.ruikao.pojo.vo.WxLoginVO;
import com.ruikao.server.service.StudentService;
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

    /**
     * 学号+密码登录（保留，管理后台导入的学生可用）
     */
    @PostMapping("/login")
    public Result<StudentLoginVO> login(@RequestBody LoginDTO loginDTO) {
        log.info("学生登录: {}", loginDTO.getUsername());

        Student student = studentService.findByStudentNo(loginDTO.getUsername());
        if (student == null) {
            throw new AccountNotFoundException("学号不存在");
        }

        if (!PasswordUtil.matches(loginDTO.getPassword(), student.getPassword())) {
            throw new PasswordErrorException("密码错误");
        }

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

    /**
     * 微信登录
     * 如果已绑定学生账号，直接登录成功返回 token
     * 如果未绑定，返回 bound=false，前端展示绑定界面
     */
    @PostMapping("/wx-login")
    public Result<WxLoginVO> wxLogin(@RequestBody WxLoginDTO wxLoginDTO) {
        log.info("微信登录，code: {}", wxLoginDTO.getCode());

        Student student = studentService.wxLogin(wxLoginDTO.getCode());

        if (student == null) {
            // 未绑定，返回 bound=false
            return Result.success(WxLoginVO.builder().bound(false).build());
        }

        // 已绑定，生成 token 返回
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, student.getId());
        claims.put(JwtClaimsConstant.USERNAME, student.getStudentNo());

        String token = JwtUtil.createJWT(
                jwtProperties.getStudentSecretKey(),
                jwtProperties.getStudentTtl(),
                claims
        );

        WxLoginVO wxLoginVO = WxLoginVO.builder()
                .bound(true)
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .name(student.getName())
                .avatar(student.getAvatar())
                .token(token)
                .build();

        return Result.success(wxLoginVO);
    }

    /**
     * 微信绑定学生账号
     * 首次微信登录后，通过学号+密码绑定已有学生账号
     */
    @PostMapping("/bind-wx")
    public Result<WxLoginVO> bindWx(@RequestBody WxBindDTO wxBindDTO) {
        log.info("微信绑定学生账号，学号: {}", wxBindDTO.getStudentNo());

        Student student = studentService.bindWx(
                wxBindDTO.getCode(),
                wxBindDTO.getStudentNo(),
                wxBindDTO.getPassword()
        );

        // 生成 token
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, student.getId());
        claims.put(JwtClaimsConstant.USERNAME, student.getStudentNo());

        String token = JwtUtil.createJWT(
                jwtProperties.getStudentSecretKey(),
                jwtProperties.getStudentTtl(),
                claims
        );

        WxLoginVO wxLoginVO = WxLoginVO.builder()
                .bound(true)
                .id(student.getId())
                .studentNo(student.getStudentNo())
                .name(student.getName())
                .avatar(student.getAvatar())
                .token(token)
                .build();

        return Result.success(wxLoginVO);
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        Long studentId = BaseContext.getCurrentId();
        log.info("学生登出: {}", studentId);
        BaseContext.remove();
        return Result.success();
    }
}
