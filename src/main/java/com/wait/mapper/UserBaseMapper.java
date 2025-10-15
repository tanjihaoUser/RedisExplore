package com.wait.mapper;

import com.wait.annotation.RedisCache;
import com.wait.entity.domain.UserBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBaseMapper {

    @RedisCache(prefix = "user:base", key = "#id", expire = 3000, returnType = UserBase.class)
    UserBase selectById(@Param("id") Long id);

    UserBase selectByUsername(@Param("username") String username);

    UserBase selectByEmail(@Param("email") String email);

    int updateLastLoginTime(@Param("userId") Long userId);

    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    Long countUsers();

    // 批量查询
    List<UserBase> selectByIds(@Param("ids") List<Long> ids);
}
