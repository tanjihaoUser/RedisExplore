package com.wait.controller;

import com.wait.entity.domain.UserDetail;
import com.wait.service.TypeCacheServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/typeCache")
@Slf4j
public class TypeCacheController {

    @Autowired
    private TypeCacheServiceImpl typeCacheService;

    @GetMapping("/classToString")
    public UserDetail classToString(@RequestParam("userId") String userId) {
        try {
            long parseUserId = Long.parseLong(userId);
            return typeCacheService.getUserName(parseUserId);
        } catch (Exception e) {
            log.warn("userId: {} is not a number", userId);
            return null;
        }
    }
}
