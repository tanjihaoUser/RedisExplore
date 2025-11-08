package com.wait.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 记录Redis中的空值标记
 * 用于区分缓存未命中(MISS)和缓存了空值(NULL_CACHE)的情况
 */
public class NullObject {

    /** String类型的空值标记 */
    public static final String NULL_STR_VALUE = "NULL";
    
    /** Hash类型的空值标记（空Map，表示缓存了空对象） */
    public static final Map<String, Boolean> NULL_HASH_VALUE = Collections.unmodifiableMap(new HashMap<>());

}
