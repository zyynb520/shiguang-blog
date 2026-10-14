package com.example.hello_spring.mapper;

import com.example.hello_spring.entity.Song;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SongMapper {

    /** 上传者昵称联表查出来，便于在界面上注明是谁推荐的 */
    String SELECT_WITH_UPLOADER =
            "SELECT s.*, "
            + "COALESCE(NULLIF(u.nickname, ''), u.username, '已注销') AS uploader_name "
            + "FROM song s LEFT JOIN `user` u ON u.id = s.user_id ";

    /** 固定按 id 排序，保证自动轮换的顺序稳定 */
    @Select(SELECT_WITH_UPLOADER + "ORDER BY s.id")
    List<Song> findAll();

    @Select(SELECT_WITH_UPLOADER + "WHERE s.id = #{id}")
    Song findById(Integer id);

    /** 查询某天被手动指定的歌曲，没有则返回 null */
    @Select(SELECT_WITH_UPLOADER + "WHERE s.recommend_date = #{date}")
    Song findByRecommendDate(LocalDate date);

    @Select("SELECT COUNT(*) FROM song")
    int countAll();

    /** 某个用户已经传了多少首（用于配额限制） */
    @Select("SELECT COUNT(*) FROM song WHERE user_id = #{userId}")
    int countByUser(Integer userId);

    /** 按 id 顺序取第 offset 首（从 0 开始），用于按日期自动轮换 */
    @Select(SELECT_WITH_UPLOADER + "ORDER BY s.id LIMIT 1 OFFSET #{offset}")
    Song findByOffset(int offset);

    @Insert("INSERT INTO song(title, artist, cover_url, audio_url, lyric, user_id) " +
            "VALUES(#{title}, #{artist}, #{coverUrl}, #{audioUrl}, #{lyric}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertSong(Song song);

    /** 清除某天已有的手动指定；唯一索引保证一天最多一首 */
    @Update("UPDATE song SET recommend_date = NULL WHERE recommend_date = #{date}")
    int clearRecommendDate(LocalDate date);

    @Update("UPDATE song SET recommend_date = #{date} WHERE id = #{id}")
    int setRecommendDate(@Param("id") Integer id, @Param("date") LocalDate date);

    @Delete("DELETE FROM song WHERE id = #{id}")
    int deleteById(Integer id);
}
