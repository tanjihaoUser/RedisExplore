package com.wait.entity.param;

import lombok.Data;

/**
 * 关注请求
 */
@Data
public class FollowRequest {
    private Long followerId;
    private Long followedId;
}

