package com.wait.mapper;

import com.wait.annotation.RedisCache;
import com.wait.entity.type.CacheType;
import com.wait.entity.domain.UserDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface UserDetailMapper {

    @RedisCache(prefix = "user:detail", key = "#userId", expire = 300,
            cacheType = CacheType.HASH, returnType = UserDetail.class)
    UserDetail selectByUserId(@Param("userId") Long userId);

    int insert(UserDetail userDetail);

    int update(UserDetail userDetail);

    int updateAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);

    int updateSignature(@Param("userId") Long userId, @Param("signature") String signature);

    int updatePreferences(@Param("userId") Long userId, @Param("preferences") Map<String, Object> preferences);
}
