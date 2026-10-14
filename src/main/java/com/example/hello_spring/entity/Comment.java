package com.example.hello_spring.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 文章评论 */
@Data
public class Comment {
    private Integer id;
    private Integer articleId;
    private Integer userId;
    private String content;
    private LocalDateTime createTime;

    /** 联表查出来的展示字段，不在 comment 表里 */
    private String authorName;
    private String authorAvatar;
    /** 作者角色：admin 的评论会在界面上标个「站长」 */
    private String authorRole;
}
