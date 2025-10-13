package com.wait.util.lock;

public interface Lock {

    public static final String LOCK_PREFIX = ":lock";

    boolean getLock(String key);

    void releaseLock(String key);
}
