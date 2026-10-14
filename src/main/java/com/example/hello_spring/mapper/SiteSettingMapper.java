package com.example.hello_spring.mapper;

import com.example.hello_spring.entity.SiteSetting;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SiteSettingMapper {

    @Select("SELECT * FROM site_setting")
    List<SiteSetting> findAll();

    @Select("SELECT setting_value FROM site_setting WHERE setting_key = #{key}")
    String findValue(String key);

    /** 存在就更新，不存在就插入 */
    @Insert("INSERT INTO site_setting(setting_key, setting_value) VALUES(#{key}, #{value}) " +
            "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)")
    void upsert(@Param("key") String key, @Param("value") String value);

    @Delete("DELETE FROM site_setting WHERE setting_key = #{key}")
    int delete(String key);
}
