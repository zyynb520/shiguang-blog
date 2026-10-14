package com.example.hello_spring.mapper;

import com.example.hello_spring.entity.Comment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper {

    /** 评论作者信息联表查出来，前端要显示昵称和头像 */
    @Select("SELECT c.*, "
            + "COALESCE(NULLIF(u.nickname, ''), u.username, '已注销') AS author_name, "
            + "u.avatar AS author_avatar, u.role AS author_role "
            + "FROM comment c LEFT JOIN `user` u ON u.id = c.user_id "
            + "WHERE c.article_id = #{articleId} ORDER BY c.create_time ASC, c.id ASC")
    List<Comment> findByArticleId(Integer articleId);

    @Select("SELECT * FROM comment WHERE id = #{id}")
    Comment findById(Integer id);

    @Select("SELECT COUNT(*) FROM comment WHERE article_id = #{articleId}")
    int countByArticle(Integer articleId);

    /** 某个用户一共发了多少条评论（用户管理页展示用） */
    @Select("SELECT COUNT(*) FROM comment WHERE user_id = #{userId}")
    int countByUser(Integer userId);

    @Insert("INSERT INTO comment(article_id, user_id, content) VALUES(#{articleId}, #{userId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertComment(Comment comment);

    @Delete("DELETE FROM comment WHERE id = #{id}")
    int deleteById(Integer id);
}
