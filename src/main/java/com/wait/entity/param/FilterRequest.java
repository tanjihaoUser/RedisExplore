package com.wait.entity.param;

import lombok.Data;

import java.util.List;

/**
 * 过滤请求
 */
@Data
public class FilterRequest {
    private Long userId;
    private List<Long> userIds;
}

