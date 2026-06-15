package com.smart.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BaseException extends RuntimeException {

    private Integer code;

    public BaseException() {
    }

    public BaseException(String message) {
        // 禁用堆栈填充，业务异常不需要堆栈信息，提升抛出性能
        super(message, null, true, false);
        this.code = 0;
    }

    public BaseException(Integer code, String message) {
        super(message, null, true, false);
        this.code = code;
    }

}
