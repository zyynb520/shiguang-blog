-- ============================================================
--  一次性迁移：单用户博客 -> 多用户（注册 / 配额 / 评论 / 头像）
--
--  只对【已经存在的库】执行一次。全新安装直接跑 schema.sql 即可，不需要这个。
--  这些 ALTER 语句不是幂等的，重复执行会报 "Duplicate column name"，属正常。
--
--  执行：mysql -u blog_user -p blog < migrate-multiuser.sql
-- ============================================================

-- 1) user 增加角色列
ALTER TABLE `user`
  ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '角色：user / admin' AFTER `avatar`;

-- 2) song 增加上传者列（历史数据为 NULL，第 4 步归给管理员）
ALTER TABLE `song`
  ADD COLUMN `user_id` INT DEFAULT NULL COMMENT '上传者 ID；老数据为 NULL' AFTER `lyric`;

ALTER TABLE `song` ADD KEY `idx_user_id` (`user_id`);

-- 3) 把站长提升为管理员
--    ⚠️ 如果你的管理员用户名不是 zyy，改成你自己的
UPDATE `user` SET `role` = 'admin' WHERE `username` = 'zyy';

-- 4) 老歌曲没有作者，统一归给管理员（否则它们的删除权限会判不清）
UPDATE `song`
  SET `user_id` = (SELECT `id` FROM `user` WHERE `role` = 'admin' ORDER BY `id` LIMIT 1)
  WHERE `user_id` IS NULL;

-- 5) comment 表补一个按用户查询的索引
ALTER TABLE `comment` ADD KEY `idx_user_id` (`user_id`);
