package com.wait.entity.vo;

import com.wait.entity.domain.UserBase;
import com.wait.entity.domain.UserDetail;
import lombok.Data;

@Data
public class UserVO {
    private UserBase baseInfo;
    private UserDetail detailInfo;

    // 便捷访问方法
    public Long getId() { return baseInfo != null ? baseInfo.getId() : null; }
    public String getUsername() { return baseInfo != null ? baseInfo.getUsername() : null; }
    public String getEmail() { return baseInfo != null ? baseInfo.getEmail() : null; }
}
