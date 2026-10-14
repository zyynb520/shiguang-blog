package com.example.hello_spring.common;

import com.example.hello_spring.entity.User;
import jakarta.servlet.http.HttpSession;

/** 登录态与权限判断的小工具，避免每个 Controller 里重复写 */
public final class Auth {

    private Auth() {
    }

    /** 取当前登录用户；未登录返回 null */
    public static User current(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (User) session.getAttribute("loginUser");
    }

    public static boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getRole());
    }

    /**
     * 当前用户能否管理「属于 ownerId 的内容」。
     * 规则：管理员可以管所有人的；普通用户只能管自己的。
     */
    public static boolean canManage(User user, Integer ownerId) {
        if (user == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return ownerId != null && ownerId.equals(user.getId());
    }
}
