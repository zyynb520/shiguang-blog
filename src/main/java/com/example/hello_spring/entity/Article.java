package com.example.hello_spring.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Article {
    private Integer id;
    private Integer userId;
    private String title;
    private String content;
    private String summary;
    private Integer viewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 联表查出来的作者信息，不在 article 表里 */
    private String authorName;
    private String authorAvatar;
    /** 作者角色：admin 的文章会在界面上标个「站长」 */
    private String authorRole;
}
