package com.wait.entity.vo;

import com.wait.entity.domain.UserBase;
import com.wait.entity.domain.UserDetail;
import lombok.Data;

/**
 * 用户视图对象（VO）
 * 用于封装用户的基本信息和详细信息
 */
@Data
public class UserVO {
    private UserBase baseInfo;
    private UserDetail detailInfo;

    /**
     * 获取用户ID
     */
    public Long getId() {
        return baseInfo != null ? baseInfo.getId() : null;
    }

    /**
     * 获取用户名
     */
    public String getUsername() {
        return baseInfo != null ? baseInfo.getUsername() : null;
    }

    /**
     * 获取邮箱
     */
    public String getEmail() {
        return baseInfo != null ? baseInfo.getEmail() : null;
    }
}
