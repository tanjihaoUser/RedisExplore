package com.wait.entity.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 帖子收藏实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFavorite {
    private Long id;
    private Long userId;
    private Long postId;
    private Long createdAt; // 收藏时间戳
}






