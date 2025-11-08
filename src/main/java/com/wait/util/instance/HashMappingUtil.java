package com.wait.util.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * HashMap和类的相互转换
 * */
@Component
@RequiredArgsConstructor
public class HashMappingUtil {

    private final ObjectMapper objectMapper;

    /**
     * 将Hash Map转换为指定类型的对象
     */
    public <T> T mapToObject(Map<String, ?> hashMap, Class<T> targetClass) {
        if (hashMap == null || hashMap.isEmpty()) {
            return null;
        }

        try {
            // 方法1: 使用Jackson直接转换（推荐）
            return objectMapper.convertValue(hashMap, targetClass);
        } catch (Exception e) {
            // 方法2: 手动映射（备用方案）
            return manualMapToObject(hashMap, targetClass);
        }
    }

    /**
     * 将对象转换为Hash Map
     */
    public <T> Map<String, Object> objectToMap(T object) {
        if (object == null) {
            return new HashMap<>();
        }

        try {
            // 使用Jackson转换（保持类型信息）
            return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // 手动转换备用方案
            return manualObjectToMap(object);
        }
    }

    /**
     * 手动映射实现（更稳定，但需要反射）
     */
    private <T> T manualMapToObject(Map<String, ?> hashMap, Class<T> targetClass) {
        try {
            T instance = targetClass.getDeclaredConstructor().newInstance();

            for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
                field.setAccessible(true);
                String fieldName = field.getName();

                if (hashMap.containsKey(fieldName)) {
                    Object value = hashMap.get(fieldName);
                    if (value != null) {
                        // 类型转换
                        Object convertedValue = convertValue(value, field.getType());
                        field.set(instance, convertedValue);
                    }
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("对象映射失败: " + targetClass.getName(), e);
        }
    }

    /**
     * 值类型转换
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // 类型相同，直接返回
        if (targetType.isInstance(value)) {
            return value;
        }

        // String转其他类型
        if (value instanceof String) {
            String strValue = (String) value;
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(strValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(strValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(strValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(strValue);
            }
        }

        // 使用Jackson进行复杂转换
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            return value; // 转换失败，返回原值
        }
    }

    private <T> Map<String, Object> manualObjectToMap(T object) {
        Map<String, Object> map = new HashMap<>();

        try {
            for (java.lang.reflect.Field field : object.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(object);
                map.put(field.getName(), value);
            }
        } catch (Exception e) {
            throw new RuntimeException("对象转Map失败: " + object.getClass().getName(), e);
        }

        return map;
    }
}
