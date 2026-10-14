package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.ArticleMapper;
import com.example.hello_spring.mapper.CommentMapper;
import com.example.hello_spring.mapper.SongMapper;
import com.example.hello_spring.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站长专用的管理接口：用户列表、禁言 / 解除禁言。
 * 所有接口都要求当前登录用户是管理员。
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final String ADMIN_ONLY = "只有站长可以操作";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Value("${app.max-users:10}")
    private int maxUsers;

    /** 用户列表（含每人发了多少内容），仅管理员 */
    @GetMapping("/users")
    public Result<Map<String, Object>> users(HttpSession session) {
        if (!Auth.isAdmin(Auth.current(session))) {
            return Result.error(ADMIN_ONLY);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userMapper.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("role", u.getRole());
            m.put("muted", u.isMuted());
            m.put("createTime", u.getCreateTime());
            m.put("articles", articleMapper.countByUser(u.getId()));
            m.put("songs", songMapper.countByUser(u.getId()));
            m.put("comments", commentMapper.countByUser(u.getId()));
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", list);
        data.put("maxUsers", maxUsers);
        return Result.success(data);
    }

    /** 禁言 / 解除禁言（仅管理员）。被禁言的人不能发表评论，但仍能浏览 */
    @PostMapping("/mute")
    public Result<String> mute(@RequestParam Integer userId,
                               @RequestParam boolean muted,
                               HttpSession session) {
        User me = Auth.current(session);
        if (!Auth.isAdmin(me)) {
            return Result.error(ADMIN_ONLY);
        }
        if (me.getId().equals(userId)) {
            return Result.error("不能禁言自己");
        }
        User target = userMapper.findById(userId);
        if (target == null) {
            return Result.error("用户不存在");
        }
        if ("admin".equals(target.getRole())) {
            return Result.error("不能禁言其他管理员");
        }

        userMapper.updateMuted(userId, muted);
        return Result.success((muted ? "已禁言 " : "已解除禁言 ") + target.displayName());
    }
}
