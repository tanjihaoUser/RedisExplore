package com.wait.entity.param;

import lombok.Data;

/**
 * 收藏请求
 */
@Data
public class FavoriteRequest {
    private Long userId;
    private Long postId;
}

