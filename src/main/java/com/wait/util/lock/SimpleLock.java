package com.wait.util.lock;

import com.wait.util.BoundUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SimpleLock implements Lock{

    @Autowired
    private BoundUtil boundUtil;

    @Override
    public boolean getLock(String key) {
        String lockKey = LOCK_PREFIX + key;
        boolean lockAcquired = boundUtil.setNx(lockKey, 1, Duration.ofSeconds(10));;
        return lockAcquired;
    }

    @Override
    public void releaseLock(String key) {

    }
}
