package com.smart.exception;

/**
 * JWT 通用异常
 */
public class JwtException extends BaseException {

    public JwtException() {
    }

    public JwtException(String msg) {
        super(msg);
    }
}