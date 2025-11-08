package com.wait.exception;

/**
 * 缓存操作异常
 * 用于封装缓存相关的错误，便于统一处理和区分业务异常
 */
public class CacheOperationException extends RuntimeException {
    
    public CacheOperationException(String message) {
        super(message);
    }
    
    public CacheOperationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public CacheOperationException(Throwable cause) {
        super(cause);
    }
}
