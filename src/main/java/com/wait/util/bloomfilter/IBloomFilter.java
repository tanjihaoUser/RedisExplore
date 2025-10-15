package com.wait.util.bloomfilter;

/**
 * 布隆过滤器统一接口
 */
public interface IBloomFilter {

    /**
     * 向过滤器中添加一个元素
     */
    boolean add(String key, String item);

    /**
     * 判断元素是否可能存在于过滤器中
     */
    boolean mightContain(String key, String item);

    /**
     * 清空指定键的布隆过滤器
     */
    void clear(String key);

    /**
     * 初始化布隆过滤器（主要针对需要预配置的实现，如RedisBloom）
     */
    void init(String key, long expectedInsertions, double fpp);
}
