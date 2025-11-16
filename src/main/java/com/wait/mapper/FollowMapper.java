package com.wait.mapper;

import com.wait.entity.domain.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FollowMapper {

    int insert(UserFollow userFollow);

    int delete(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    List<Long> selectFollowedIds(Long followerId);

    boolean exists(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    int countFollowers(Long followedId);

    int countFollowing(Long followerId);
}
