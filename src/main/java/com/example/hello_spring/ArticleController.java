package com.example.hello_spring;

import com.example.hello_spring.common.ArticleViewRecorder;
import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.Article;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.ArticleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /** 浏览量的去重策略，见 {@link ArticleViewRecorder} */
    @Autowired
    private ArticleViewRecorder viewRecorder;

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

    /**
     * 文章详情（公开）。
     *
     * <p><b>刻意不在这里 +1 浏览量。</b>GET 按 HTTP 语义必须是"安全的"（不改变服务端状态），
     * 一旦在这里写库，浏览器预取、爬虫、扫描器、以及任何一次刷新都会把浏览量刷上去。
     * 浏览量改由前端在正文真正渲染完之后调 {@code POST /article/{id}/view} 上报。
     */
    @GetMapping("/detail/{id}")
    public Result<Article> detail(@PathVariable Integer id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            return Result.error("文章不存在");
        }
        return Result.success(article);
    }

    /**
     * 上报一次浏览（公开，不需要登录）。
     *
     * <p>和「读详情」拆开的好处：
     * <ul>
     *   <li>GET 恢复成纯读，可以被缓存、被预取，计数不受影响；</li>
     *   <li>爬虫和扫描器不会执行 JavaScript，天然不计入；</li>
     *   <li>只有真正把正文渲染出来的访问才算，加载失败的页面不算。</li>
     * </ul>
     *
     * <p>是否真的 +1 由 {@link ArticleViewRecorder} 判定：
     * 同一浏览器 24 小时内、同一 IP 10 分钟内对同一篇文章只计一次。
     *
     * @return 记录后的最新浏览量，前端直接拿它刷新页面上的数字
     */
    @PostMapping("/{id}/view")
    public Result<Integer> view(@PathVariable Integer id,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            return Result.error("文章不存在");
        }

        int current = article.getViewCount() == null ? 0 : article.getViewCount();
        if (viewRecorder.shouldCount(request, response, id)) {
            articleMapper.increaseViewCount(id);
            current++;
        }
        return Result.success(current);
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

        articleMapper.deleteById(id);
        return Result.success("已删除《" + article.getTitle() + "》");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
