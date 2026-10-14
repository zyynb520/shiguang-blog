package com.example.hello_spring.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日推歌 */
@Data
public class Song {
    private Integer id;
    private String title;
    private String artist;
    /** 封面图访问路径，如 /uploads/cover/xxx.jpg，可为空 */
    private String coverUrl;
    /** 音频文件访问路径，如 /uploads/audio/xxx.mp3 */
    private String audioUrl;
    private String lyric;
    /** 上传者 ID；老数据为 NULL */
    private Integer userId;
    /** 手动指定的推荐日期；为空则参与「按日期自动轮换」 */
    private LocalDate recommendDate;
    private LocalDateTime createTime;

    /** 联表查出来的上传者昵称，不在 song 表里 */
    private String uploaderName;
}
