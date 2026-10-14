package com.example.hello_spring.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Integer id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    /** user 普通用户 / admin 管理员 */
    private String role;
    /** 是否被站长禁言：true = 不能评论 */
    private boolean muted;
    private LocalDateTime createTime;

    /** 是不是管理员。管理员可以管理所有人的内容 */
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** 展示用的名字：优先昵称，没有就用用户名 */
    public String displayName() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return username;
    }
}
