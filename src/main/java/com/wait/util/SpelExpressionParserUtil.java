package com.wait.util;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

@Component
@Slf4j
public class SpelExpressionParserUtil {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 解析SpEL表达式
     */
    public Object parseSpel(ProceedingJoinPoint joinPoint, String expression) {
        log.info("before parse, exp: {}", expression);
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            Object target = joinPoint.getTarget();

            // 创建评估上下文
            EvaluationContext context = new MethodBasedEvaluationContext(
                    target, method, args, parameterNameDiscoverer);

            // 设置方法参数到上下文（关键修复）
            setMethodParametersToContext(method, args, context);

            // 添加自定义函数和变量
            context.setVariable("root", target);
            context.setVariable("method", method);
            context.setVariable("args", args);

            // 解析表达式
            Expression expr = parser.parseExpression(expression);
            log.info("after parse, exp: {}", expr.getValue(context));
            return expr.getValue(context);

        } catch (Exception e) {
            throw new RuntimeException("SpEL表达式解析失败: " + expression, e);
        }
    }

    /**
     * 将方法参数设置到SpEL上下文中 - 解决SpEL表达式中无法使用方法参数的问题
     */
    private void setMethodParametersToContext(Method method, Object[] args, EvaluationContext context) {
        // 获取参数名（支持@Param注解和编译时参数名）
        String[] paramNames = getParameterNames(method);

        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            Object paramValue = i < args.length ? args[i] : null;

            // 设置参数到上下文，支持 #paramName 和 paramName 两种方式
            context.setVariable(paramName, paramValue);

            // 同时设置p0, p1, p2...格式的变量，增强兼容性
            context.setVariable("p" + i, paramValue);

            log.debug("Set parameter to context: {} = {} (type: {})",
                    paramName, paramValue, paramValue != null ? paramValue.getClass().getSimpleName() : "null");
        }
    }

    /**
     * 获取方法参数名（支持@Param注解）
     */
    private String[] getParameterNames(Method method) {
        // 首先尝试从@Param注解获取参数名
        Parameter[] parameters = method.getParameters();
        String[] paramNames = new String[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];

            // 检查@Param注解
            Param paramAnnotation = param.getAnnotation(Param.class);
            if (paramAnnotation != null && !paramAnnotation.value().isEmpty()) {
                paramNames[i] = paramAnnotation.value();
            } else {
                // 使用方法的参数名
                paramNames[i] = param.getName();
            }

            // 如果参数名无效，使用默认名称
            if (paramNames[i] == null || paramNames[i].trim().isEmpty() || "arg".equals(paramNames[i])) {
                paramNames[i] = "p" + i;
            }
        }

        return paramNames;
    }

    /**
     * 评估条件表达式
     */
    public boolean evaluateCondition(ProceedingJoinPoint joinPoint, String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return true; // 无条件则默认通过
        }

        try {
            Object result = parseSpel(joinPoint, condition);
            return result instanceof Boolean ? (Boolean) result : false;

        } catch (Exception e) {
            log.warn("条件表达式评估失败: {}, 默认返回false", condition, e);
            return false;
        }
    }

    /**
     * 生成缓存Key，默认返回string类型
     */
    public String generateCacheKey(ProceedingJoinPoint joinPoint, String keyExpression, String prefix) {
        if (keyExpression == null || keyExpression.trim().isEmpty()) {
            // 默认Key生成策略
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return String.format("%s::%s::%s",
                    prefix,
                    signature.getMethod().getName(),
                    Arrays.hashCode(joinPoint.getArgs()));
        }

        Object keyValue = parseSpel(joinPoint, keyExpression);
        log.info("parsed key: {}", keyValue);
        if (!StringUtil.isNullOrEmpty(prefix)) {
            return prefix + ":" + (keyValue != null ? keyValue.toString() : "null");
        }
        return keyValue != null ? keyValue.toString() : "null";
    }
}
