package com.example.hello_spring.mapper;

import com.example.hello_spring.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT * FROM user")
    List<User> findAll();

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM `user` WHERE id = #{id}")
    User findById(Integer id);

    @Insert("INSERT INTO `user`(username, password, nickname) VALUES(#{username}, #{password}, #{nickname})")
    void insertUser(User user);

    /** 用户总数：用于判断注册名额是否已满 */
    @Select("SELECT COUNT(*) FROM user")
    int countAll();

    @Update("UPDATE `user` SET avatar = #{avatar} WHERE id = #{id}")
    int updateAvatar(@Param("id") Integer id, @Param("avatar") String avatar);

    @Update("UPDATE `user` SET nickname = #{nickname} WHERE id = #{id}")
    int updateNickname(@Param("id") Integer id, @Param("nickname") String nickname);

    @Update("UPDATE `user` SET muted = #{muted} WHERE id = #{id}")
    int updateMuted(@Param("id") Integer id, @Param("muted") boolean muted);
}
