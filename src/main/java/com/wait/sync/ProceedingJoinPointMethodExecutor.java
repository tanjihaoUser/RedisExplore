package com.wait.sync;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于ProceedingJoinPoint的MethodExecutor实现
 * 用于在AOP切面中包装ProceedingJoinPoint
 */
@Slf4j
public class ProceedingJoinPointMethodExecutor implements MethodExecutor {

    private final ProceedingJoinPoint joinPoint;
    private final Method method;
    private final Map<Integer, String> paramIndexToName;
    private final boolean isVoidMethod;

    public ProceedingJoinPointMethodExecutor(ProceedingJoinPoint joinPoint) {
        this.joinPoint = joinPoint;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        this.method = signature.getMethod();
        this.isVoidMethod = method.getReturnType() == void.class;
        this.paramIndexToName = buildParamIndexToName();
    }

    @Override
    public Object execute(Object... args) throws Throwable {
        if (args != null && args.length > 0) {
            return joinPoint.proceed(args);
        } else {
            return joinPoint.proceed();
        }
    }

    @Override
    public Object[] getArgs() {
        return joinPoint.getArgs();
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Map<Integer, String> getParamIndexToName() {
        return paramIndexToName;
    }

    @Override
    public boolean isVoidMethod() {
        return isVoidMethod;
    }

    /**
     * 解析位置到参数名的映射
     * 基于 MyBatis @Param 注解解析"参数下标 -> 参数名"
     * 没有注解使用参数名，参数名解析失败使用默认命名（arg0, arg1, ...）
     */
    private Map<Integer, String> buildParamIndexToName() {
        Map<Integer, String> map = new HashMap<>();
        try {
            MethodSignature ms = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = ms.getParameterNames();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            for (int i = 0; i < parameterAnnotations.length; i++) {
                String paramName = null;

                // 1. 优先使用 @Param 注解的值
                for (Annotation annotation : parameterAnnotations[i]) {
                    if (annotation instanceof Param) {
                        paramName = ((Param) annotation).value();
                        break;
                    }
                }

                // 2. 如果没有 @Param 注解，使用参数名
                if (paramName == null && parameterNames != null && i < parameterNames.length) {
                    paramName = parameterNames[i];
                }

                // 3. 如果参数名也不可用，使用默认命名（参数索引）
                if (paramName == null) {
                    paramName = "arg" + i;
                }

                map.put(i, paramName);
            }
        } catch (Exception e) {
            log.warn("解析参数名失败: {}", e.getMessage());
        }
        return map;
    }
}
