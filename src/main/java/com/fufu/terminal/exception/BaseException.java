package com.fufu.terminal.exception;

import lombok.Getter;

/**
 * 应用程序基础异常类
 * 所有自定义异常都应继承此类
 * @author lizelin
 */
@Getter
public abstract class BaseException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    private final Object data;
    
    protected BaseException(String errorCode, String message, int httpStatus) {
        this(errorCode, message, httpStatus, null, null);
    }
    
    protected BaseException(String errorCode, String message, int httpStatus, Throwable cause) {
        this(errorCode, message, httpStatus, cause, null);
    }
    
    protected BaseException(String errorCode, String message, int httpStatus, Throwable cause, Object data) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.data = data;
    }
}