package com.wait.util.bloomfilter;

import org.springframework.stereotype.Component;

/**
 * 分布式布隆过滤器，生成的代码有问题，应该是使用较少，这里就不做实现了
 * */
@Component("redisBloomFilter")
public class RedisBloomFilter implements IBloomFilter {


    @Override
    public boolean add(String key, String item) {
        return false;
    }

    @Override
    public boolean mightContain(String key, String item) {
        return false;
    }

    @Override
    public void clear(String key) {

    }

    @Override
    public void init(String key, long expectedInsertions, double fpp) {

    }
}