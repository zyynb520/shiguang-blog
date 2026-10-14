package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.Comment;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.ArticleMapper;
import com.example.hello_spring.mapper.CommentMapper;
import com.example.hello_spring.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文章评论。
 * 读接口公开（游客能看评论），写接口需要登录（见 WebConfig 拦截规则）。
 */
@RestController
@RequestMapping("/comment")
public class CommentController {

    /** comment.content 列是 varchar(500) */
    private static final int MAX_LENGTH = 500;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private UserMapper userMapper;

    /** 某篇文章的评论列表（公开） */
    @GetMapping("/list")
    public Result<List<Comment>> list(@RequestParam Integer articleId) {
        return Result.success(commentMapper.findByArticleId(articleId));
    }

    /** 发表评论（需登录） */
    @PostMapping("/add")
    public Result<Comment> add(@RequestBody Comment body, HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            return Result.error("请先登录后再评论");
        }

        // 禁言状态必须实时查库，不能信 Session 里的旧对象 ——
        // 否则站长刚禁言的人只要不重新登录就还能接着评论
        User fresh = userMapper.findById(me.getId());
        if (fresh != null && fresh.isMuted()) {
            return Result.error("你已被站长禁言，暂时不能发表评论");
        }
        if (body.getArticleId() == null) {
            return Result.error("缺少文章 ID");
        }
        if (body.getContent() == null || body.getContent().isBlank()) {
            return Result.error("评论内容不能为空");
        }
        String content = body.getContent().trim();
        if (content.length() > MAX_LENGTH) {
            return Result.error("评论最多 " + MAX_LENGTH + " 个字");
        }
        if (articleMapper.findById(body.getArticleId()) == null) {
            return Result.error("文章不存在");
        }

        Comment c = new Comment();
        c.setArticleId(body.getArticleId());
        c.setUserId(me.getId());
        c.setContent(content);
        commentMapper.insertComment(c);

        // 补齐展示字段，前端可以直接渲染
        c.setAuthorName(fresh != null ? fresh.displayName() : me.displayName());
        c.setAuthorAvatar(fresh != null ? fresh.getAvatar() : me.getAvatar());
        c.setAuthorRole(fresh != null ? fresh.getRole() : me.getRole());
        return Result.success(c);
    }

    /** 删除评论（只能删自己的；管理员可以删所有人的） */
    @PostMapping("/delete")
    public Result<String> delete(@RequestParam Integer id, HttpSession session) {
        User me = Auth.current(session);
        Comment c = commentMapper.findById(id);
        if (c == null) {
            return Result.error("评论不存在");
        }
        if (!Auth.canManage(me, c.getUserId())) {
            return Result.error("只能删除自己的评论");
        }
        commentMapper.deleteById(id);
        return Result.success("已删除评论");
    }
}
