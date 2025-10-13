package com.wait.util;

import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
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

            // 添加自定义函数和变量
            context.setVariable("root", target);
            context.setVariable("method", method);
            context.setVariable("args", args);

            // 解析表达式
            Expression expr = parser.parseExpression(expression);
            return expr.getValue(context);

        } catch (Exception e) {
            throw new RuntimeException("SpEL表达式解析失败: " + expression, e);
        }
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
        if (!StringUtil.isNullOrEmpty(prefix)) {
            return prefix + ":" + (keyValue != null ? keyValue.toString() : "null");
        }
        return keyValue != null ? keyValue.toString() : "null";
    }
}
