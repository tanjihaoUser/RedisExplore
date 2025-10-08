package com.wait.aspect;

import com.wait.annotation.RateLimit;
import com.wait.config.LimitType;
import com.wait.util.RateLimiter;
import com.wait.util.limit.SlideWindow;
import com.wait.util.limit.TokenBucket;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import static com.wait.util.RateLimiter.LIMIT_STR;

import java.lang.reflect.Method;

/**
 * 限流切面
 */
@Aspect
@Component
@Order(1) // 确保在事务等切面之前执行
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Autowired
    private SlideWindow slideWindow;

    @Autowired
    private TokenBucket tokenBucket;

//    @Autowired
//    private LeakyBucket leakyBucket;

    // 切点表达式，rateLimit是通知方法参数的名称，将方法的注解绑定到通知方法参数上，不需要和注解名保持一致，但需要和注解的参数名保持一致
    // 通过 ProceedingJoinPoint 对象调用 proceed() 方法，实现对原始方法的调用。如省略该参数，原始方法将无法执行
    // 使用 proceed() 方法调用原始方法时，因无法预知原始方法运行过程中是否会出现异常，强制抛出 Throwable 对象，封装原始方法中可能出现的异常信息
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
//        Object[] args = joinPoint.getArgs(); // 参数数组
//        Signature signature = joinPoint.getSignature();
//        String name = signature.getName();
//        String kind = joinPoint.getKind();
//        log.info("ProceedingJoinPoint: {}, args: {}, signature: {}, method: {}, kind: {}",
//                joinPoint, args, signature, name, kind);

        // 检查是否启用限流
        if (!rateLimit.enabled()) {
            return joinPoint.proceed();
        }

        // 解析限流key（支持SpEL表达式）
        String key = resolveKey(joinPoint, rateLimit);

        // 获取对应的限流器
        RateLimiter limiter = getRateLimiter(rateLimit.type());

        // 执行限流检查
        boolean allowed = limiter.allowRequest(key, rateLimit.limit(), rateLimit.window(), rateLimit.unit());

        // 被限流，抛异常或返回特定结果
        if (!allowed) {
            return handleRateLimited(joinPoint, rateLimit, key);
        }

//        log.info("before invoke...");
        // 执行目标方法
        try {
            Object result = joinPoint.proceed();
//            log.info("method execute, key: {} - {}, result: {}", key, joinPoint.getSignature().getName(), result);
            return result;
        } catch (Exception e) {
            log.error("方法执行异常: {}", key, e);
            throw e;
//        } finally {
//            log.info("after invoke...");
        }
    }

    /**
     * 解析限流key，支持SpEL表达式
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String keyExpression = rateLimit.key();

        // 如果key为空，使用默认key（类名+方法名）
        if (keyExpression.isEmpty()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return String.format("%s.%s",
                    signature.getDeclaringType().getSimpleName(),
                    signature.getMethod().getName());
        }

        // 解析SpEL表达式
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            Object target = joinPoint.getTarget();

            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    target, method, args, discoverer);

            Object value = parser.parseExpression(keyExpression).getValue(context);
            return value != null ? LIMIT_STR + value : keyExpression;
        } catch (Exception e) {
            log.warn("解析SpEL表达式失败: {}, 使用原表达式作为key", keyExpression, e);
            return keyExpression;
        }
    }

    /**
     * 根据类型获取对应的限流器
     */
    private RateLimiter getRateLimiter(LimitType type) {
        switch (type) {
            case TOKEN_BUCKET:
                return tokenBucket;
//            case LEAKY_BUCKET:
//                return leakyBucket;
            case SLIDE_WINDOW:
            default:
                return slideWindow;
        }
    }

    /**
     * 处理被限流的请求
     */
    private String handleRateLimited(ProceedingJoinPoint joinPoint, RateLimit rateLimit, String key) {
        String errorMessage = rateLimit.message();
        log.debug("request limit: key = {}, limit = {} / {} {}, method = {}",
                key, rateLimit.limit(), rateLimit.window(), rateLimit.unit(),
                joinPoint.getSignature().getName());
        return errorMessage;

//        // 抛出限流异常
//        throw new RateLimitException(key, rateLimit.limit(), rateLimit.window(),
//                rateLimit.unit(), errorMessage);
    }
}