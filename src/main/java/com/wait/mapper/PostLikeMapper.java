package com.wait.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wait.entity.domain.PostLike;

@Mapper
public interface PostLikeMapper {

    int insert(PostLike postLike);

    /**
     * 批量插入（用于批量持久化）
     */
    int batchInsert(List<PostLike> likes);

    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    boolean exists(@Param("postId") Long postId, @Param("userId") Long userId);

    /**
     * 批量查询存在性：返回已存在的 (postId, userId) 对
     * 用于批量写入前检查，避免重复插入
     * 
     * @param likes 待检查的点赞关系列表
     * @return 已存在的点赞关系列表（只包含 postId 和 userId）
     */
    List<PostLike> batchExists(@Param("likes") List<PostLike> likes);

    int countByPostId(Long postId);
}
