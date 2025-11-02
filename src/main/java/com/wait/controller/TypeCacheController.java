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
        long start = 0;
        long end = 0;
        Set<String> result = null;
        try {
            start = System.currentTimeMillis();
            result = boundUtil.keys("*");
            end = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("testTimeout, start: {}, end: {}, error: {}", start, end, e.getMessage());
        }
        return result;
    }

    @GetMapping("/string")
    public UserBase objectToString(@RequestParam("userId") String userId) {
        try {
            long parseUserId = Long.parseLong(userId);
            return typeCacheService.getBaseWithString(parseUserId);
        } catch (Exception e) {
            log.warn("userId: {} is not a number", userId);
            return null;
        }
    }

    @GetMapping("/hash")
    public UserDetail objectToHash(@RequestParam("userId") String userId) {
        try {
            long parseUserId = Long.parseLong(userId);
            return typeCacheService.getDetailWithHash(parseUserId);
        } catch (Exception e) {
            log.warn("userId: {} is not a number", userId);
            return null;
        }
    }

}
