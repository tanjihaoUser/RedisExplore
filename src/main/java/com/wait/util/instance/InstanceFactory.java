package com.wait.util.instance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实例工厂 - 安全创建对象实例
 * 支持各种边界情况处理，包括无空参构造方法的类
 */
@Component
@Slf4j
public class InstanceFactory {

    // 缓存构造方法，提高性能
    private final Map<Class<?>, Constructor<?>> constructorCache = new ConcurrentHashMap<>();

    // 基本类型默认值映射
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = new HashMap<>();

    static {
        // 初始化基本类型默认值
        PRIMITIVE_DEFAULTS.put(int.class, 0);
        PRIMITIVE_DEFAULTS.put(long.class, 0L);
        PRIMITIVE_DEFAULTS.put(double.class, 0.0);
        PRIMITIVE_DEFAULTS.put(float.class, 0.0f);
        PRIMITIVE_DEFAULTS.put(boolean.class, false);
        PRIMITIVE_DEFAULTS.put(byte.class, (byte) 0);
        PRIMITIVE_DEFAULTS.put(short.class, (short) 0);
        PRIMITIVE_DEFAULTS.put(char.class, '\0');
    }

    /**
     * 安全创建实例 - 主要方法
     * 创建失败返回null，不会抛出异常
     */
    public <T> T createInstanceSafely(Class<T> clazz) {
        if (clazz == null) {
            log.warn("尝试创建null类的实例");
            return null;
        }

        try {
            return createInstanceInternal(clazz);
        } catch (Exception e) {
            log.warn("创建实例失败: {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * 创建实例 - 严格模式（失败时抛出异常）
     */
    public <T> T createInstance(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        T instance = createInstanceInternal(clazz);
        if (instance == null) {
            throw new RuntimeException("无法创建实例: " + clazz.getName());
        }
        return instance;
    }

    /**
     * 内部创建实例实现
     */
    private <T> T createInstanceInternal(Class<T> clazz) {
        // 1. 检查是否可实例化
        if (!isInstantiable(clazz)) {
            return handleUninstantiableType(clazz);
        }

        // 2. 尝试使用空参构造方法（最高优先级）
        try {
            Constructor<T> constructor = getNoArgConstructor(clazz);
            if (constructor != null) {
                log.debug("使用空参构造方法创建实例: {}", clazz.getName());
                return constructor.newInstance();
            }
        } catch (Exception e) {
            log.debug("空参构造方法失败，尝试其他方式: {}", clazz.getName());
        }

        // 3. 尝试使用有参构造方法（传入默认值）
        try {
            return createWithParameterizedConstructor(clazz);
        } catch (Exception e) {
            log.debug("有参构造方法失败: {}", clazz.getName());
        }

        // 4. 尝试使用序列化方式（如果类可序列化）
        try {
            return createViaSerialization(clazz);
        } catch (Exception e) {
            log.debug("序列化方式失败: {}", clazz.getName());
        }

        // 5. 所有方法都失败
        log.error("所有实例化方法都失败: {}", clazz.getName());
        return null;
    }

    /**
     * 获取空参构造方法
     */
    @SuppressWarnings("unchecked")
    private <T> Constructor<T> getNoArgConstructor(Class<T> clazz) {
        return (Constructor<T>) constructorCache.computeIfAbsent(clazz, key -> {
            try {
                Constructor<T> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor;
            } catch (NoSuchMethodException e) {
                return null; // 没有空参构造方法
            }
        });
    }

    /**
     * 检查类是否可实例化
     */
    private boolean isInstantiable(Class<?> clazz) {
        if (clazz.isInterface()) {
            log.debug("接口无法直接实例化: {}", clazz.getName());
            return false;
        }

        if (clazz.isAnnotation()) {
            log.debug("注解无法实例化: {}", clazz.getName());
            return false;
        }

        if (Modifier.isAbstract(clazz.getModifiers())) {
            log.debug("抽象类无法实例化: {}", clazz.getName());
            return false;
        }

        if (clazz.isArray()) {
            log.debug("数组类型需要特殊处理: {}", clazz.getName());
            return false;
        }

        if (clazz.isEnum()) {
            log.debug("枚举类型需要特殊处理: {}", clazz.getName());
            return false;
        }

        return true;
    }

    /**
     * 处理不可实例化的类型
     */
    @SuppressWarnings("unchecked")
    private <T> T handleUninstantiableType(Class<T> clazz) {
        if (clazz.isInterface()) {
            log.debug("为接口创建动态代理: {}", clazz.getName());
            return createProxyForInterface(clazz);
        }

        if (clazz.isArray()) {
            log.debug("创建空数组: {}", clazz.getName());
            return (T) Array.newInstance(clazz.getComponentType(), 0);
        }

        if (clazz.isEnum()) {
            log.debug("返回枚举的第一个值: {}", clazz.getName());
            T[] constants = clazz.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : null;
        }

        return null;
    }

    /**
     * 为接口创建动态代理
     */
    @SuppressWarnings("unchecked")
    private <T> T createProxyForInterface(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    // 返回方法返回类型的默认值
                    Class<?> returnType = method.getReturnType();
                    if (returnType.isPrimitive()) {
                        return PRIMITIVE_DEFAULTS.get(returnType);
                    }
                    return null;
                }
        );
    }

    /**
     * 使用有参构造方法创建实例
     */
    @SuppressWarnings("unchecked")
    private <T> T createWithParameterizedConstructor(Class<T> clazz) {
        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();

            // 优先选择参数最少的构造方法
            Constructor<?> bestConstructor = null;
            int minParams = Integer.MAX_VALUE;

            for (Constructor<?> constructor : constructors) {
                int paramCount = constructor.getParameterCount();
                if (paramCount < minParams) {
                    minParams = paramCount;
                    bestConstructor = constructor;
                }
            }

            if (bestConstructor != null) {
                bestConstructor.setAccessible(true);
                Object[] args = createDefaultArgs(bestConstructor.getParameterTypes());
                log.debug("使用有参构造方法创建实例: {} (参数个数: {})", clazz.getName(), args.length);
                return (T) bestConstructor.newInstance(args);
            }

        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.debug("有参构造方法实例化失败: {}", clazz.getName(), e);
        }

        return null;
    }

    /**
     * 为构造方法参数创建默认值
     */
    private Object[] createDefaultArgs(Class<?>[] parameterTypes) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = getDefaultValue(parameterTypes[i]);
        }
        return args;
    }

    /**
     * 获取类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        // 基本类型返回默认值
        if (type.isPrimitive()) {
            return PRIMITIVE_DEFAULTS.get(type);
        }

        // 字符串返回空字符串
        if (type == String.class) {
            return "";
        }

        // 其他对象类型返回null
        return null;
    }

    /**
     * 通过序列化方式创建实例（绕过构造方法）
     */
    @SuppressWarnings("unchecked")
    private <T> T createViaSerialization(Class<T> clazz) {
        if (!Serializable.class.isAssignableFrom(clazz)) {
            log.debug("类未实现Serializable接口: {}", clazz.getName());
            return null;
        }

        try {
            // 创建序列化数据
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);

            // 写入一个"模板"对象（通过其他方式先创建一个）
            T template = createTemplateInstance(clazz);
            if (template == null) {
                return null;
            }

            oos.writeObject(template);
            oos.close();

            // 反序列化创建新实例
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (T) ois.readObject();

        } catch (Exception e) {
            log.debug("序列化方式创建实例失败: {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * 创建模板实例（用于序列化）
     */
    private <T> T createTemplateInstance(Class<T> clazz) {
        // 尝试各种方式创建模板实例
        try {
            // 尝试使用第一个构造方法（即使有参数）
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            if (constructors.length > 0) {
                Constructor<?> constructor = constructors[0];
                constructor.setAccessible(true);

                Object[] args = new Object[constructor.getParameterCount()];
                // 为参数设置合理的默认值
                for (int i = 0; i < args.length; i++) {
                    args[i] = getReasonableDefault(constructor.getParameterTypes()[i]);
                }

                return (T) constructor.newInstance(args);
            }
        } catch (Exception e) {
            log.debug("创建模板实例失败: {}", clazz.getName(), e);
        }

        return null;
    }

    /**
     * 获取合理的默认值（用于模板实例创建）
     */
    private Object getReasonableDefault(Class<?> type) {
        if (type == String.class) return "template";
        if (type == Integer.class || type == int.class) return 0;
        if (type == Long.class || type == long.class) return 0L;
        if (type == Boolean.class || type == boolean.class) return false;
        if (type == Double.class || type == double.class) return 0.0;
        if (type == Float.class || type == float.class) return 0.0f;

        // 对于其他对象类型，尝试创建实例
        return createInstanceSafely(type);
    }

    /**
     * 创建集合类型的空实例
     */
    @SuppressWarnings("unchecked")
    public <T> T createEmptyCollection(Class<T> clazz) {
        if (clazz == null) return null;

        try {
            if (clazz.isAssignableFrom(java.util.List.class) || clazz == java.util.List.class) {
                return (T) new java.util.ArrayList<>();
            }
            if (clazz.isAssignableFrom(java.util.Set.class) || clazz == java.util.Set.class) {
                return (T) new java.util.HashSet<>();
            }
            if (clazz.isAssignableFrom(java.util.Map.class) || clazz == java.util.Map.class) {
                return (T) new java.util.HashMap<>();
            }
            if (clazz.isAssignableFrom(java.util.Collection.class) || clazz == java.util.Collection.class) {
                return (T) new java.util.ArrayList<>();
            }

            // 如果不是集合类型，尝试普通实例化
            return createInstanceSafely(clazz);

        } catch (Exception e) {
            log.warn("创建空集合失败: {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * 检查类是否有空参构造方法
     */
    public boolean hasNoArgConstructor(Class<?> clazz) {
        try {
            clazz.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}