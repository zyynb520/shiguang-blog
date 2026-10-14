package com.example.hello_spring.mapper;

import com.example.hello_spring.entity.Article;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper {

    /** 作者昵称联表查出来，列表和详情都要显示是谁写的 */
    String SELECT_WITH_AUTHOR =
            "SELECT a.*, "
            + "COALESCE(NULLIF(u.nickname, ''), u.username, '已注销') AS author_name, "
            + "u.avatar AS author_avatar, u.role AS author_role "
            + "FROM article a LEFT JOIN `user` u ON u.id = a.user_id ";

    @Insert("INSERT INTO article(user_id, title, content, summary) VALUES(#{userId}, #{title}, #{content}, #{summary})")
    void insertArticle(Article article);

    @Select(SELECT_WITH_AUTHOR + "ORDER BY a.create_time DESC, a.id DESC")
    List<Article> findAllArticles();

    @Select(SELECT_WITH_AUTHOR + "WHERE a.id = #{id}")
    Article findById(Integer id);

    /** 某个用户已经发了多少篇（用于配额限制） */
    @Select("SELECT COUNT(*) FROM article WHERE user_id = #{userId}")
    int countByUser(Integer userId);

    /**
     * 浏览数 +1。
     *
     * <p>显式写上 {@code update_time = update_time} 是刻意的：表上该列有
     * {@code ON UPDATE CURRENT_TIMESTAMP}，而"浏览一次"也算 UPDATE，
     * 不这样写的话 update_time 会变成"最后一次被浏览的时间"，
     * 而不是"最后一次被编辑的时间"。显式赋值可以让 MySQL 跳过自动更新。
     *
     * @return 受影响行数：0 表示该 id 的文章不存在
     */
    @Update("UPDATE article SET view_count = view_count + 1, update_time = update_time WHERE id = #{id}")
    int increaseViewCount(Integer id);

    @Delete("DELETE FROM article WHERE id = #{id}")
    int deleteById(Integer id);

    /** 删文章时把它的评论一起清掉，避免留下孤儿数据 */
    @Delete("DELETE FROM comment WHERE article_id = #{id}")
    int deleteCommentsByArticleId(Integer id);

    /**
     * 更新文章内容。
     * 只改标题/正文/摘要，不动作者和浏览数；update_time 由表上的
     * ON UPDATE CURRENT_TIMESTAMP 自动刷新。
     *
     * @return 受影响行数：0 表示该 id 的文章不存在
     */
    @Update("UPDATE article SET title = #{title}, content = #{content}, summary = #{summary} WHERE id = #{id}")
    int updateArticle(@Param("id") Integer id,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("summary") String summary);
}
