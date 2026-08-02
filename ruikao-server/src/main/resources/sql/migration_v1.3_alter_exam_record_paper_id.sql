-- 睿考系统数据库迁移脚本 v1.3
-- 将 exam_record.paper_id 改为可空，兼容考试未关联试卷的边界情况
-- 同时添加索引优化查询性能

ALTER TABLE `exam_record` MODIFY COLUMN `paper_id` INT NULL COMMENT '试卷ID';
ALTER TABLE `exam_record` ADD INDEX `idx_er_paper` (`paper_id`);