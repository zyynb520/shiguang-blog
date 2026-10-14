-- ============================================================
--  一次性迁移：给用户加「禁言」标记
--
--  只对【已经存在的库】执行一次。全新安装直接跑 schema.sql 即可。
--  重复执行会报 "Duplicate column name"，属正常。
--
--  执行：mysql -u blog_user -p blog < migrate-mute.sql
-- ============================================================

ALTER TABLE `user`
  ADD COLUMN `muted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被禁言：1 = 不能评论' AFTER `role`;
