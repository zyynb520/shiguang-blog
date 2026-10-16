-- ============================================================
--  拾光小站 · 数据库结构
--
--  全新部署：
--     CREATE DATABASE blog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--     mysql -u bloguser -p blog < schema.sql
--
--  已存在的库也可以直接跑（全部是 CREATE TABLE IF NOT EXISTS，不会删数据）
-- ============================================================

-- ------------------------------------------------------------
--  用户表
--  password 存的是 PBKDF2 摘要（pbkdf2$sha256$迭代次数$盐$哈希），不是明文。
--  摘要当前长度 87，这里留到 255，将来调高迭代次数也不用改表。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
  `password`    VARCHAR(255) NOT NULL                COMMENT 'PBKDF2 密码摘要',
  `nickname`    VARCHAR(50)  DEFAULT NULL            COMMENT '昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL            COMMENT '头像访问路径，如 /uploads/avatar/xxx.jpg',
  `role`        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '角色：user 普通用户 / admin 管理员',
  `muted`       TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否被禁言：1 = 不能评论',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 兼容老库：早期 password 只有 varchar(100)，装不下 87 字符的摘要再留余量
ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) NOT NULL COMMENT 'PBKDF2 密码摘要';

-- ------------------------------------------------------------
--  文章表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article` (
  `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     INT          NOT NULL                COMMENT '作者 ID',
  `title`       VARCHAR(100) NOT NULL                COMMENT '标题',
  `content`     TEXT         NOT NULL                COMMENT '正文（Markdown 源码）',
  `summary`     VARCHAR(200) DEFAULT NULL            COMMENT '摘要，留空时前端自动从正文截取',
  `view_count`  INT          DEFAULT 0               COMMENT '浏览次数',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- ------------------------------------------------------------
--  歌曲表：支撑「每日推歌」
--  recommend_date 为空表示没被手动指定；某天没有手动指定的歌曲时，
--  后端按日期在所有歌曲里自动轮换（同一天全站结果一致）。
--  唯一索引允许多个 NULL，因此等效于「同一天最多手动指定一首」。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `song` (
  `id`             INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`          VARCHAR(150) NOT NULL                COMMENT '歌名',
  `artist`         VARCHAR(150) DEFAULT NULL            COMMENT '歌手',
  `cover_url`      VARCHAR(500) DEFAULT NULL            COMMENT '封面访问路径，如 /uploads/cover/xxx.jpg',
  `audio_url`      VARCHAR(500) NOT NULL                COMMENT '音频访问路径，如 /uploads/audio/xxx.mp3',
  `lyric`          TEXT                                 COMMENT '歌词，可留空',
  `user_id`        INT          DEFAULT NULL            COMMENT '上传者 ID；老数据为 NULL',
  `recommend_date` DATE         DEFAULT NULL            COMMENT '手动指定的推荐日期；为空则参与自动轮换',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recommend_date` (`recommend_date`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日推歌曲库';

-- ------------------------------------------------------------
--  评论表
--  没有建外键：删除文章时由后端代码把该文章的评论一并清掉。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `comment` (
  `id`          INT          NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id`  INT          NOT NULL                COMMENT '文章 ID',
  `user_id`     INT          NOT NULL                COMMENT '评论人 ID',
  `content`     VARCHAR(500) NOT NULL                COMMENT '评论内容',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ------------------------------------------------------------
--  站点配置表（键值对）
--  目前用到七个键：
--    bgImage      背景图访问路径，如 /uploads/bg/xxx.jpg，没有这行代表用默认极光背景
--    bgDim        背景图上黑色遮罩的深浅，0~100，越大字越清楚
--    logoImage    站点图标访问路径，如 /uploads/logo/xxx.png；
--                 同时用于顶栏站名的圆形标记和浏览器标签页图标，没有这行就用内置渐变圆点
--    accent       主题主色，如 #ff7ac6
--    accent2      主题辅色
--    accent3      主题点缀色
--    themePreset  配色来源标记（sakura / ocean / forest / sunset / violet / mint / custom / fromImage）
--  做成键值对是为了以后加新配置项不用改表结构。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `site_setting` (
  `setting_key`   VARCHAR(64) NOT NULL                COMMENT '配置项名',
  `setting_value` TEXT                                COMMENT '配置项值',
  `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点配置';
