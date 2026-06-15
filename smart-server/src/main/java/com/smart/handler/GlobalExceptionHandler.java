package com.smart.handler;

import com.smart.exception.BaseException;
import com.smart.exception.SystemException;
import com.smart.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice(basePackages = "com.smart.controller")
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常
     * 业务异常属于预期内的逻辑分支，使用 WARN 级别，不打印堆栈，防止告警风暴
     *
     * @param request HTTP 请求对象（可用于获取 URL、IP 等上下文）
     * @param ex      业务异常
     * @return 统一错误结果
     */
    @ExceptionHandler(BaseException.class)
    public Result<?> handleBaseException(HttpServletRequest request, BaseException ex) {
        // 业务异常只记录 WARN，打印请求路径和异常信息，不打印堆栈
        log.warn("业务异常拦截 | URI: {} | 提示信息: {}", request.getRequestURI(), ex.getMessage());

        // 如果业务异常携带了自定义错误码，则使用自定义错误码；否则默认使用 0
        if (ex.getCode() != null && ex.getCode() != 0) {
            return Result.error(ex.getCode(), ex.getMessage());
        }
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获自定义系统异常
     * 系统异常属于非预期错误（如第三方接口超时、DB 宕机），必须使用 ERROR 级别并打印完整堆栈
     *
     * @param request HTTP 请求对象
     * @param ex      系统异常
     * @return 统一错误结果
     */
    @ExceptionHandler(SystemException.class)
    public Result<?> handleSystemException(HttpServletRequest request, SystemException ex) {
        // 系统异常记录 ERROR，必须携带上下文参数和完整堆栈 (ex 作为最后一个参数传入)
        log.error("系统异常拦截 | URI: {} | 错误描述: {}", request.getRequestURI(), ex.getMessage(), ex);

        // 对外隐藏内部系统错误细节，防止敏感信息泄露
        return Result.error("系统繁忙，请稍后再试");
    }

    /**
     * 捕获 JSR-380 参数校验异常 (RequestBody 校验失败)
     *
     * @param e 参数校验异常
     * @return 统一错误结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        // 提取第一个校验失败的错误信息
        String message = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("参数校验失败 | 提示信息: {}", message);
        return Result.error(400, message);
    }

    /**
     * 捕获 JSR-380 参数校验异常 (表单/URL 参数校验失败)
     *
     * @param e 绑定异常
     * @return 统一错误结果
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        log.warn("参数绑定校验失败 | 提示信息: {}", message);
        return Result.error(400, message);
    }

    /**
     * 兜底：捕获所有未处理的系统级 Exception
     * 防止未知异常导致前端收到 500 原始错误页
     *
     * @param request HTTP 请求对象
     * @param ex      未知异常
     * @return 统一错误结果
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(HttpServletRequest request, Exception ex) {
        log.error("未知系统异常拦截 | URI: {} | 异常类型: {}", request.getRequestURI(), ex.getClass().getName(), ex);
        return Result.error("系统内部错误，请联系管理员");
    }
}
