package com.wait.controller;

import com.wait.entity.domain.UserBase;
import com.wait.entity.domain.UserDetail;
import com.wait.service.impl.TypeCacheServiceImpl;
import com.wait.util.BoundUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 测试Redis基础类型的缓存与转换
 * */
@RestController
@RequestMapping("/typeCache")
@Slf4j
public class TypeCacheController {

    @Autowired
    private TypeCacheServiceImpl typeCacheService;

    @Autowired
    private BoundUtil boundUtil;

    @GetMapping("/testTimeout")
    public Set<String> testTimeout() {
        long start = System.currentTimeMillis();
        try {
            Set<String> result = boundUtil.keys("*");
            long end = System.currentTimeMillis();
            log.info("testTimeout completed in {} ms", end - start);
            return result;
        } catch (Exception e) {
            long end = System.currentTimeMillis();
            log.error("testTimeout failed after {} ms", end - start, e);
            throw e; // 让全局异常处理器处理
        }
    }

    @GetMapping("/string")
    public UserBase objectToString(@RequestParam("userId") String userId) {
        try {
            long parseUserId = Long.parseLong(userId);
            return typeCacheService.getBaseWithString(parseUserId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId format: {}", userId);
            throw new IllegalArgumentException("userId must be a valid number: " + userId, e);
        }
    }

    @GetMapping("/hash")
    public UserDetail objectToHash(@RequestParam("userId") String userId) {
        try {
            long parseUserId = Long.parseLong(userId);
            return typeCacheService.getDetailWithHash(parseUserId);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId format: {}", userId);
            throw new IllegalArgumentException("userId must be a valid number: " + userId, e);
        }
    }

}
