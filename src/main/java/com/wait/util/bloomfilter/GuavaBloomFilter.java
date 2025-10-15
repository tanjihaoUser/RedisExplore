package com.wait.util.bloomfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Google Guava 的单机版布隆过滤器实现
 * 特点：内存型，速度快，应用重启后数据丢失
 * 处理流程：
 *     系统启动时，或者通过定时任务，将数据库中所有可能被查询的数据的Key（如所有商品的ID、所有用户的ID）预先加载到布隆过滤器中。
 *     请求到达时，先查询布隆过滤器，如果布隆过滤器中不存在，则直接返回；如果布隆过滤器中存在，则继续查询数据库。
 * 关于数据同步：
 *     系统启动时全量初始化：
 *          适合数据量不大、更新不频繁的场景（如商品分类、城市列表）。
 *          在服务启动时，一次性从数据库加载所有有效ID到布隆过滤器。
 *     定时任务全量/增量同步：
 *          全量同步：每天凌晨低峰期，重建整个布隆过滤器。简单但有一定延迟。
 *          增量同步：更优雅的方案。通过监听数据库的Binlog（如使用Canal、Debezium等工具），当有新增数据时（INSERT），实时地将新ID添加到布隆过滤器
 *      当有删除数据时（DELETE），如果布隆过滤器支持删除（如计数布隆过滤器），则进行删除。这是最理想的方案。
 *      双写：
 *          在业务代码中，每当向数据库插入一条新记录时，同时将其ID添加到布隆过滤器中。这种方式强依赖业务代码，有耦合性。
 */
@Component("guavaBloomFilter") // 指定Bean名称，便于注入
public class GuavaBloomFilter implements IBloomFilter {

    // 使用ConcurrentHashMap来管理多个布隆过滤器实例（以key区分）
    private final ConcurrentHashMap<String, BloomFilter<String>> filterMap = new ConcurrentHashMap<>();

    @Override
    public boolean add(String key, String item) {
        BloomFilter<String> filter = filterMap.get(key);
        if (filter == null) {
            // 如果不存在，使用默认参数创建一个（通常应该先调用init）
            throw new IllegalStateException("Bloom filter for key '" + key + "' is not initialized. Call init() first.");
        }
        return filter.put(item);
    }

    @Override
    public boolean mightContain(String key, String item) {
        BloomFilter<String> filter = filterMap.get(key);
        return filter != null && filter.mightContain(item);
    }

    @Override
    public void clear(String key) {
        filterMap.remove(key);
    }

    @Override
    public void init(String key, long expectedInsertions, double fpp) {
        // 使用Guava构建布隆过滤器
        BloomFilter<String> newFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );
        filterMap.put(key, newFilter);
    }

    /**
     * Guava特有的方法：获取当前布隆过滤器的近似元素数量（可选）
     */
    public long approximateElementCount(String key) {
        BloomFilter<String> filter = filterMap.get(key);
        return filter != null ? filter.approximateElementCount() : 0L;
    }
}
