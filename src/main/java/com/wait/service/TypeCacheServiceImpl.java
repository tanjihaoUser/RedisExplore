package com.wait.service;

import com.wait.entity.domain.UserDetail;
import com.wait.mapper.UserDetailMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TypeCacheServiceImpl {

    @Autowired
    private UserDetailMapper userDetailMapper;

    public UserDetail getUserName(long userId) {
        return userDetailMapper.selectByUserId(userId);
    }
}
