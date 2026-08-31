package com.ruikao.server.controller.admin;

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
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.vo.LoginVO;
import com.ruikao.server.annotation.OperLog;
import com.ruikao.server.security.LoginAttemptService;
import com.ruikao.server.security.TokenBlacklistService;
import com.ruikao.server.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@Slf4j
public class AdminAuthController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @OperLog(module = "登录认证", type = "登录", description = "登录:{#loginDTO.username}")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        log.info("管理员登录: {}", loginDTO.getUsername());

        // 限流：连续失败 5 次锁定 15 分钟
        loginAttemptService.checkNotLocked(loginDTO.getUsername());

        SysUser user = sysUserService.findByUsername(loginDTO.getUsername());
        if (user == null) {
            loginAttemptService.recordFailure(loginDTO.getUsername());
            throw new AccountNotFoundException("账号不存在");
        }

        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(loginDTO.getUsername());
            throw new PasswordErrorException("密码错误");
        }

        // 禁用账号禁止登录
        if (user.getStatus() != ExamConstants.USER_STATUS_ENABLED) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        loginAttemptService.recordSuccess(loginDTO.getUsername());
        // 供操作日志切面记录操作人
        BaseContext.setCurrentUsername(user.getRealName());

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.USERNAME, user.getUsername());
        claims.put(JwtClaimsConstant.USER_TYPE, user.getUserType());

        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );

        LoginVO loginVO = new LoginVO();
        loginVO.setId(user.getId());
        loginVO.setUsername(user.getUsername());
        loginVO.setRealName(user.getRealName());
        loginVO.setAvatar(user.getAvatar());
        loginVO.setToken(token);
        loginVO.setUserType(user.getUserType());

        return Result.success(loginVO);
    }

    @OperLog(module = "登录认证", type = "登出", description = "登出")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        // 登出即失效：将当前 token 加入黑名单，剩余有效期内不可再用
        String token = request.getHeader(jwtProperties.getAdminTokenName());
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        tokenBlacklistService.blacklist("admin", token, jwtProperties.getAdminTtl());
        BaseContext.remove();
        return Result.success();
    }

    @GetMapping("/info")
    public Result<SysUser> info() {
        Long userId = BaseContext.getCurrentId();
        log.info("获取当前用户信息, userId: {}", userId);
        SysUser sysUser = sysUserService.getById(userId);
        return Result.success(sysUser);
    }
}
