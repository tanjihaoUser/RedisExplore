package com.wait.util;

import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * 解析注解中的SpEL表达式
 */
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

            // 创建标准评估上下文
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 设置方法参数到上下文（修复版本）
            setMethodParametersToContext(method, args, context);

            // 设置特殊变量
            context.setVariable("method", method);
            context.setVariable("args", args);
            context.setVariable("target", joinPoint.getTarget());

            // 如果只有一个参数，设置默认变量
            if (args.length == 1) {
                context.setVariable("value", args[0]);
                context.setVariable("#value", args[0]);
                context.setRootObject(args[0]);
            }

            // 解析表达式
            Expression expr = parser.parseExpression(expression);
            Object result = expr.getValue(context);

            log.debug("SpEL parsed finish: {} -> {}", expression, result);
            return result;

        } catch (Exception e) {
            log.error("SpEL parse failed: {}, error: {}", expression, e.getMessage(), e);
            throw new RuntimeException("SpEL parse failed: " + expression + ", 错误: " + e.getMessage(), e);
        }
    }

    /**
     * 将方法参数设置到SpEL上下文中
     * 支持通过 #参数名 或 参数名 访问参数值
     * 支持通过 #参数名.属性 访问参数对象的属性（如 #sessionId、#userSession.userId）
     */
    private void setMethodParametersToContext(Method method, Object[] args, StandardEvaluationContext context) {
        String[] paramNames = getParameterNames(method);
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            Object paramValue = i < args.length ? args[i] : null;

            if (paramValue == null) {
                continue;
            }

            // 设置参数名到上下文（支持两种访问方式）
            // 1. 直接通过参数名访问：参数名 或 #参数名
            context.setVariable(paramName, paramValue);           // userSession 或 arg0
            context.setVariable("#" + paramName, paramValue);    // #userSession 或 #arg0

            // 2. 如果参数名是默认名称（arg0, arg1等），根据参数类型推断一个合理的变量名
            // 这样即使编译时没有保留参数名，表达式也能正常工作
            if (paramName.startsWith("arg") && i < parameters.length) {
                Class<?> paramType = parameters[i].getType();
                String inferredName = inferVariableNameFromType(paramType);
                if (inferredName != null && !inferredName.equals(paramName)) {
                    context.setVariable(inferredName, paramValue);           // userSession
                    context.setVariable("#" + inferredName, paramValue);    // #userSession
                }
            }

            // 可选：设置索引变量（兼容旧代码，如 p0, a0）
            context.setVariable("p" + i, paramValue);            // p0
            context.setVariable("a" + i, paramValue);           // a0

        }

        // 如果只有一个参数，设置根对象，方便表达式直接访问属性
        if (args.length == 1 && args[0] != null) {
            context.setRootObject(args[0]);
        }
    }

    /**
     * 获取方法参数名（支持多种方式，优先级从高到低）：
     * 1. @Param 注解指定的参数名（最高优先级，用于 MyBatis Mapper）
     * 2. Spring ParameterNameDiscoverer 发现的参数名（可从调试信息中获取，即使没有 -parameters）
     * 3. 编译时保留的实际参数名（通过 Parameter.getName()，需要编译时开启 -parameters）
     * 4. 默认命名（arg0, arg1, ...）
     * 
     * 使用示例：
     * - @Param("sessionId") String id -> 参数名为 "sessionId"
     * - String userSession (有调试信息或 -parameters) -> 参数名为 "userSession"
     * - String id (无编译信息) -> 参数名为 "arg0"
     * 
     * 注意：要获取真实参数名，有两种方式：
     * 1. 在 Maven 中配置编译参数（推荐）：
     *    <compilerArgs>
     *      <arg>-parameters</arg>
     *    </compilerArgs>
     * 2. 编译时保留调试信息（-g:vars），ParameterNameDiscoverer 可以从调试信息中获取
     */
    private String[] getParameterNames(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] paramNames = new String[parameters.length];

        // 预先获取所有可能的信息
        String[] discoveredNames = parameterNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];

            // 优先级1：@Param 注解（最高优先级）
            Param paramAnnotation = param.getAnnotation(Param.class);
            if (paramAnnotation != null && !paramAnnotation.value().isEmpty()) {
                paramNames[i] = paramAnnotation.value();
                log.debug("Parameter {}: using @Param annotation name: {}", i, paramNames[i]);
                continue;
            }

            // 优先级2：使用 Spring ParameterNameDiscoverer（可从调试信息中获取，无需 -parameters）
            // 这个方法会尝试从字节码的调试信息（LocalVariableTable）中获取参数名
            if (discoveredNames != null && i < discoveredNames.length && 
                discoveredNames[i] != null && !discoveredNames[i].startsWith("arg")) {
                paramNames[i] = discoveredNames[i];
                log.debug("Parameter {}: using discovered name from debug info: {}", i, paramNames[i]);
                continue;
            }

            // 优先级3：尝试使用反射获取的实际参数名（需要编译时开启 -parameters）
            String actualName = param.getName();
            if (actualName != null && !actualName.startsWith("arg")) {
                paramNames[i] = actualName;
                log.debug("Parameter {}: using actual parameter name (requires -parameters): {}", i, paramNames[i]);
                continue;
            }

            // 优先级4：默认命名（arg0, arg1, ...）
            paramNames[i] = "arg" + i;
            log.debug("Parameter {}: using default name: {}", i, paramNames[i]);
        }

        return paramNames;
    }

    /**
     * 评估条件表达式
     */
    public boolean evaluateCondition(ProceedingJoinPoint joinPoint, String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
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
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return String.format("%s::%s::%s",
                    prefix,
                    signature.getMethod().getName(),
                    Arrays.hashCode(joinPoint.getArgs()));
        }

        try {
            // 如果表达式以#开头但参数名是arg0，尝试自动转换
            String processedExpression = preprocessExpression(joinPoint, keyExpression);

            Object keyValue = parseSpel(joinPoint, processedExpression);
            if (keyValue == null) {
                log.warn("SpEL parse result is null: {}", processedExpression);
                keyValue = "null";
            }

            String keyString = keyValue.toString();
            if (StringUtils.hasText(prefix)) {
                return prefix + ":" + keyString;
            }
            return keyString;

        } catch (Exception e) {
            log.error("生成缓存Key失败, expression: {}, prefix: {}", keyExpression, prefix, e);
            // 返回一个兜底的key，避免影响主流程
            return (prefix != null ? prefix : "cache") + ":error:" + System.currentTimeMillis();
        }
    }

    /**
     * 预处理表达式：如果表达式中的变量名在上下文中找不到，尝试从参数类型推断
     * 例如：#userSession.sessionId，如果 userSession 不存在，尝试从参数类型匹配
     */
    private String preprocessExpression(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return expression;
        }

        // 提取表达式中的变量名（如 #userSession.sessionId -> userSession）
        String variableName = extractVariableName(expression);
        if (variableName == null) {
            return expression;
        }

        // 检查是否需要处理：如果变量名不是以 arg 开头，说明可能是用户期望的参数名
        // 这种情况下，需要确保参数已经正确映射
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        // 检查参数类型，尝试匹配变量名
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            if (args[i] == null) {
                continue;
            }

            Class<?> paramType = parameters[i].getType();
            // 如果参数类型匹配变量名（首字母小写的类名）
            String inferredName = inferVariableNameFromType(paramType);
            if (variableName.equals(inferredName)) {
                // 变量名匹配，但可能参数名是 arg0，需要确保映射正确
                // 这里不需要修改表达式，因为 setMethodParametersToContext 会处理
                break;
            }
        }

        return expression;
    }

    /**
     * 从表达式中提取变量名
     * 例如：#userSession.sessionId -> userSession, #sessionId -> sessionId
     */
    private String extractVariableName(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        expression = expression.trim();

        // 处理 #变量名.属性 的情况
        if (expression.startsWith("#")) {
            String rest = expression.substring(1);
            int dotIndex = rest.indexOf('.');
            if (dotIndex > 0) {
                return rest.substring(0, dotIndex);
            }
            return rest;
        }

        // 处理不带 # 的情况
        int dotIndex = expression.indexOf('.');
        if (dotIndex > 0) {
            return expression.substring(0, dotIndex);
        }

        return expression;
    }

    /**
     * 根据类型推断变量名（首字母小写的类名）
     * 例如：UserSession -> userSession
     */
    private String inferVariableNameFromType(Class<?> type) {
        if (type == null) {
            return null;
        }

        String simpleName = type.getSimpleName();
        if (simpleName == null || simpleName.isEmpty()) {
            return null;
        }

        // 将类名首字母小写：UserSession -> userSession
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 调试方法：打印方法参数信息
     */
    public void debugMethodParameters(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        log.info("=== 方法参数调试信息 ===");
        log.info("方法名: {}", method.getName());
        log.info("参数数量: {}", args.length);

        String[] paramNames = getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            log.info("参数 {} [{}]: {} (类型: {})",
                    i, paramNames[i], arg,
                    arg != null ? arg.getClass().getName() : "null");
        }
        log.info("=== 调试信息结束 ===");
    }
}