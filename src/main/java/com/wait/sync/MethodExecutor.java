package com.wait.sync;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 通用的方法执行器接口，用于封装方法执行逻辑
 * 替代ProceedingJoinPoint，使策略类更加通用和可测试
 */
public interface MethodExecutor {

    /**
     * 执行方法
     * 
     * @param args 方法参数，如果为null则使用原始参数
     * @return 方法执行结果
     * @throws Throwable 执行异常
     */
    Object execute(Object... args) throws Throwable;

    /**
     * 获取方法参数
     * 
     * @return 方法参数数组
     */
    Object[] getArgs();

    /**
     * 获取方法签名
     * 
     * @return 方法对象
     */
    Method getMethod();

    /**
     * 获取参数名到索引的映射（用于HASH类型缓存的字段映射）
     * 
     * @return 参数名到索引的映射
     */
    Map<Integer, String> getParamIndexToName();

    /**
     * 判断方法是否是void返回类型
     * 
     * @return true如果是void方法
     */
    boolean isVoidMethod();
}
