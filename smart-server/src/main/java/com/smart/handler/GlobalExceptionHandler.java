package com.smart.handler;

import com.smart.exception.BaseException;
import com.smart.exception.SystemException;
import com.smart.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice(basePackages = "com.smart.controller")
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex 业务异常
     * @return 错误信息
     */
    @ExceptionHandler(BaseException.class)
    public Result<T> exceptionHandler(HttpServletRequest request, BaseException ex){
        log.error("业务异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获系统异常
     * @param ex 系统异常
     * @return 错误信息
     */
    @ExceptionHandler(SystemException.class)
    public Result<T> exceptionHandler(HttpServletRequest request, SystemException ex){
        log.error("系统异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
}
