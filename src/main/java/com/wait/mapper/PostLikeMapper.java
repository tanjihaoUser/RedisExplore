package com.wait.mapper;

import com.wait.entity.domain.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

    int insert(PostLike postLike);

    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    boolean exists(@Param("postId") Long postId, @Param("userId") Long userId);

    int countByPostId(Long postId);
}
