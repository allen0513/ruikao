package com.ruikao.server.handler;

import com.ruikao.common.exception.*;
import com.ruikao.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public Result<String> handleAccountNotFound(AccountNotFoundException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(PasswordErrorException.class)
    public Result<String> handlePasswordError(PasswordErrorException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
