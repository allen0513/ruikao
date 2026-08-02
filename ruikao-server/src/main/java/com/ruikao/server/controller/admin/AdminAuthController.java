package com.ruikao.server.controller.admin;

import com.ruikao.common.constant.JwtClaimsConstant;
import com.ruikao.common.context.BaseContext;
import com.ruikao.common.exception.AccountNotFoundException;
import com.ruikao.common.exception.PasswordErrorException;
import com.ruikao.common.properties.JwtProperties;
import com.ruikao.common.result.Result;
import com.ruikao.common.utils.JwtUtil;
import com.ruikao.common.utils.PasswordUtil;
import com.ruikao.pojo.dto.LoginDTO;
import com.ruikao.pojo.entity.SysUser;
import com.ruikao.pojo.vo.LoginVO;
import com.ruikao.server.service.SysUserService;
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

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        log.info("管理员登录: {}", loginDTO.getUsername());

        SysUser user = sysUserService.findByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new AccountNotFoundException("账号不存在");
        }

        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new PasswordErrorException("密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.USERNAME, user.getUsername());

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

    @PostMapping("/logout")
    public Result<String> logout() {
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
