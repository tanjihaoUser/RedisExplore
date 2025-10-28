package com.wait.entity;

import com.wait.entity.type.CacheStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CacheResult<T> {
    private final T value;
    private final CacheStatus status;

    public static <T> CacheResult<T> trans(T value) {
        if (value == null) {
            return new CacheResult<>(null, CacheStatus.MISS);
        }
        return new CacheResult<>(value, CacheStatus.HIT);
    }

    public static <T> CacheResult<T> nullCache() {
        return new CacheResult<>(null, CacheStatus.NULL_CACHE);
    }

    public boolean isHit() {
        return status != CacheStatus.MISS;
    }
}
