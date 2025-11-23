package com.wait.mapper;

import com.wait.entity.domain.PostFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostFavoriteMapper {

    /**
     * 插入收藏记录
     */
    int insert(PostFavorite postFavorite);

    /**
     * 删除收藏记录
     */
    int delete(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 检查是否存在
     */
    boolean exists(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 批量查询存在性：返回已存在的 (userId, postId) 对
     * 用于批量写入前检查，避免重复插入
     * 
     * @param favorites 待检查的收藏关系列表
     * @return 已存在的收藏关系列表（只包含 userId 和 postId）
     */
    List<PostFavorite> batchExists(@Param("favorites") List<PostFavorite> favorites);

    /**
     * 根据帖子ID统计收藏数
     */
    int countByPostId(Long postId);

    /**
     * 根据用户ID查询收藏的帖子ID列表
     */
    List<Long> selectPostIdsByUserId(Long userId);

    /**
     * 批量插入（用于数据恢复/同步）
     */
    int batchInsert(List<PostFavorite> favorites);
}



