package com.wait.entity.param;

import lombok.Data;

/**
 * 点赞请求
 */
@Data
public class LikeRequest {
    private Long userId;
    private Long postId;
}

