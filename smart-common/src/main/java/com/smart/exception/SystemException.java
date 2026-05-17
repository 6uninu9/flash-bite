package com.smart.exception;

/**
 * 系统异常：表示由基础设施、依赖服务、环境等导致的不可恢复或需要人工介入的错误。
 * 例如：数据库连接失败、消息队列不可用、Redis 超时、文件读写错误等。
 * <p>
 * 该异常为 unchecked exception，通常由全局异常处理器捕获并记录日志，返回通用的系统错误提示给客户端。
 */
public class SystemException extends RuntimeException{

    public SystemException() {
        super();
    }

    public SystemException(String msg) {
        super(msg);
    }

    public SystemException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public SystemException(Throwable cause) {
        super(cause);
    }
}
