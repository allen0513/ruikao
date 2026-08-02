package com.ruikao.server.handler;

import com.ruikao.common.exception.*;
import com.ruikao.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /** 参数校验失败（@Valid 触发），返回 400 + 第一条错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest().body(Result.error(msg));
    }

    /** 方法参数校验失败（@Validated 触发） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest().body(Result.error(msg));
    }

    /** 请求体缺失/JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<String>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("请求参数格式错误"));
    }

    /** 上传文件超出大小限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<String>> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件超出大小限制: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.error("上传文件大小超出限制"));
    }

    /** 数据库完整性约束冲突（外键/唯一键/非空），返回 409 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error("操作被拒绝：数据存在关联引用或字段不合法"));
    }

    /**
     * 兜底系统异常：返回 500，避免服务器错误伪装成 HTTP 200 的业务响应。
     * 说明：业务异常（BusinessException 系列）保持 HTTP 200 + body code=0 的约定式设计，
     * 前端按 body.code 分支处理；仅未知系统异常提升为 500 便于监控与前端区分。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("系统繁忙，请稍后重试"));
    }
}