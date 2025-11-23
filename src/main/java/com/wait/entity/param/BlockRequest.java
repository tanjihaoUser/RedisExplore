package com.wait.entity.param;

import lombok.Data;

/**
 * 拉黑请求
 */
@Data
public class BlockRequest {
    private Long userId;
    private Long blockedUserId;
}

