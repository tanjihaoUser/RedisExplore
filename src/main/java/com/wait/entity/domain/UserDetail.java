package com.wait.entity.domain;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UserDetail {
    private Long id;
    private Long userId;
    private String realName;
    private Integer gender;      // 0-未知 1-男 2-女
    private LocalDate birthday;
    private String avatar;
    private String signature;
    private String country;
    private String province;
    private String city;
    private String address;
    private String postalCode;
    private Map<String, Object> preferences; // JSON格式的偏好设置
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 性别枚举
    public enum Gender {
        UNKNOWN(0), MALE(1), FEMALE(2);

        private final int code;
        Gender(int code) { this.code = code; }
        public int getCode() { return code; }
    }
}
