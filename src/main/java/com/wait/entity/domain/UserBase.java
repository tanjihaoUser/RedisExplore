package com.wait.entity.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserBase {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private Integer status;      // 0-禁用 1-正常 2-冻结
    private Integer userType;    // 1-普通用户 2-VIP用户 3-管理员
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastLoginTime;

    // 状态枚举
    public enum Status {
        DISABLED(0), NORMAL(1), FROZEN(2);

        private final int code;
        Status(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    // 用户类型枚举
    public enum UserType {
        NORMAL(1), VIP(2), ADMIN(3);

        private final int code;
        UserType(int code) { this.code = code; }
        public int getCode() { return code; }
    }
}