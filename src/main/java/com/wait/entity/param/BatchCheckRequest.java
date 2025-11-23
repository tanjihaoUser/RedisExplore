package com.wait.entity.param;

import lombok.Data;

import java.util.List;

/**
 * 批量检查请求
 */
@Data
public class BatchCheckRequest {
    private Long userId;
    private List<Long> postIds;
}

