package com.wait.util.lock;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.wait.util.BoundUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单的Redis分布式锁实现
 * 使用SETNX实现，设置10秒过期时间防止死锁
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimpleLock implements Lock {

    private final BoundUtil boundUtil;

    // 锁的默认过期时间（秒）
    private static final long DEFAULT_LOCK_TTL_SECONDS = 10L;

    @Override
    public boolean getLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        boolean lockAcquired = boundUtil.setNx(lockKey, 1, Duration.ofSeconds(DEFAULT_LOCK_TTL_SECONDS));
        if (lockAcquired) {
            log.debug("Lock acquired: {}", lockKey);
        } else {
            log.debug("Lock acquisition failed: {}", lockKey);
        }
        return lockAcquired;
    }

    @Override
    public void releaseLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        boolean deleted = boundUtil.del(lockKey);
        if (deleted) {
            log.debug("Lock released: {}", lockKey);
        } else {
            log.warn("Lock release failed (may have expired): {}", lockKey);
        }
    }
}
