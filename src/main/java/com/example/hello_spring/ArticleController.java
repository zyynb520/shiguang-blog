package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.Article;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.ArticleMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
public class ArticleController {

    @Autowired
    private ArticleMapper articleMapper;

    /** 每个用户最多发布多少篇文章 */
    @Value("${app.max-articles-per-user:10}")
    private int maxArticlesPerUser;

    /** 标题列是 varchar(100)，摘要列是 varchar(200) */
    private static final int MAX_TITLE = 100;
    private static final int MAX_SUMMARY = 200;

    @PostMapping("/publish")
    public Result<String> publish(@RequestBody Article article, HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            // 拦截器已挡一层，这里再兜底
            return Result.error("请先登录");
        }
        if (isBlank(article.getTitle()) || isBlank(article.getContent())) {
            return Result.error("标题和内容不能为空");
        }
        if (article.getTitle().trim().length() > MAX_TITLE) {
            return Result.error("标题最多 " + MAX_TITLE + " 个字");
        }
        if (article.getSummary() != null && article.getSummary().length() > MAX_SUMMARY) {
            return Result.error("摘要最多 " + MAX_SUMMARY + " 个字");
        }

        int mine = articleMapper.countByUser(me.getId());
        if (mine >= maxArticlesPerUser) {
            return Result.error("每人最多发布 " + maxArticlesPerUser + " 篇文章，你已经发满了");
        }

        article.setTitle(article.getTitle().trim());
        article.setUserId(me.getId());
        articleMapper.insertArticle(article);
        return Result.success("发布成功（已发 " + (mine + 1) + "/" + maxArticlesPerUser + " 篇）");
    }

    @GetMapping("/list")
    public Result<List<Article>> list() {
        return Result.success(articleMapper.findAllArticles());
    }

    @GetMapping("/detail/{id}")
    public Result<Article> detail(@PathVariable Integer id) {
        // 先把浏览数 +1：受影响行数为 0 说明这篇文章不存在
        if (articleMapper.increaseViewCount(id) == 0) {
            return Result.error("文章不存在");
        }
        return Result.success(articleMapper.findById(id));
    }

    /**
     * 更新文章（需登录，且只能改自己的；管理员可以改所有人的）。
     *
     * <p>只改标题、摘要、正文三样，**不动作者也不动浏览数**，
     * 所以编辑一篇文章不会把它的浏览量清零。
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody Article article, HttpSession session) {
        User me = Auth.current(session);
        if (article.getId() == null) {
            return Result.error("缺少文章 ID");
        }
        if (isBlank(article.getTitle()) || isBlank(article.getContent())) {
            return Result.error("标题和内容不能为空");
        }
        if (article.getTitle().trim().length() > MAX_TITLE) {
            return Result.error("标题最多 " + MAX_TITLE + " 个字");
        }
        if (article.getSummary() != null && article.getSummary().length() > MAX_SUMMARY) {
            return Result.error("摘要最多 " + MAX_SUMMARY + " 个字");
        }

        Article existing = articleMapper.findById(article.getId());
        if (existing == null) {
            return Result.error("文章不存在");
        }
        if (!Auth.canManage(me, existing.getUserId())) {
            return Result.error("只能编辑自己发布的文章");
        }

        articleMapper.updateArticle(article.getId(), article.getTitle().trim(),
                article.getContent(), article.getSummary());
        return Result.success("已更新《" + article.getTitle().trim() + "》");
    }

    /**
     * 删除文章（需登录，且只能删自己的；管理员可以删所有人的）。
     *
     * <p>文章正文是 Markdown 源码，里面的图片都是外链，服务器上没有对应文件，
     * 所以这里只需要删掉数据库记录，外加把它的评论一起清掉。
     */
    @PostMapping("/delete")
    public Result<String> delete(@RequestParam Integer id, HttpSession session) {
        User me = Auth.current(session);
        Article article = articleMapper.findById(id);
        if (article == null) {
            return Result.error("文章不存在");
        }
        if (!Auth.canManage(me, article.getUserId())) {
            return Result.error("只能删除自己发布的文章");
        }

        int comments = articleMapper.deleteCommentsByArticleId(id);
        articleMapper.deleteById(id);

        String msg = "已删除《" + article.getTitle() + "》";
        if (comments > 0) {
            msg += "，同时清掉了 " + comments + " 条评论";
        }
        return Result.success(msg);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
