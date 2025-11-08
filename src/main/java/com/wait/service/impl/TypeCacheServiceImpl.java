package com.wait.service.impl;

import com.wait.entity.domain.UserBase;
import com.wait.entity.domain.UserDetail;
import com.wait.mapper.UserBaseMapper;
import com.wait.mapper.UserDetailMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基础类型存储Redis及获取
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TypeCacheServiceImpl {

    private final UserDetailMapper userDetailMapper;

    private final UserBaseMapper userBaseMapper;

    public UserBase getBaseWithString(long userId) {
        return userBaseMapper.selectById(userId);
    }

    public UserDetail getDetailWithHash(long userId) {
        return userDetailMapper.selectByUserId(userId);
    }
}
