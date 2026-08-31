/*
 * ===================================================================
 * 在线考试管理系统 - 完整数据库初始化脚本
 * ===================================================================
 * 数据库名称: exam_system
 * 字符集: utf8mb4
 * 排序规则: utf8mb4_unicode_ci
 *
 * 使用说明：
 * 1. 在 Navicat 中创建一个新连接，新建数据库 exam_system（字符集 utf8mb4）
 * 2. 右键数据库 -> 运行 SQL 文件 -> 选择本文件
 * 3. 或者直接打开本文件执行全部查询
 *
 * 说明：本脚本与后端实体（ruikao-pojo 的 entity）严格一一对应，
 * 共 19 张表：
 *   - 系统管理：sys_role, sys_user, sys_user_role
 *   - 人员管理：student, class_info
 *   - 考试安排：exam
 *   - 在线考试：question_bank, exam_paper, paper_question,
 *              exam_student, exam_record, exam_answer
 *   - 业务扩展：subject, knowledge_point, question_knowledge_point,
 *              learning_material, campus_news, comment
 *   - 操作日志：sys_oper_log
 *
 * 考试记录状态模型：1-考试中 2-已交卷(待批改) 3-已批改(待审核) 4-已审核(成绩终态)
 *
 * 内置默认账号（密码均为 123456）：
 *   管理员：admin / admin1
 *   教师端：teacher1 / teacher2
 *   学生端：学号 202501~202505，密码 123456
 * ===================================================================
 */

-- ==================== 初始化设置 ====================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 创建数据库 ====================
-- 如尚未创建数据库，请先执行：
-- CREATE DATABASE IF NOT EXISTS `exam_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE `exam_system`;

-- ================================================================
-- 1. 系统管理
-- ================================================================

-- ----------------------------
-- 1.1 角色表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name`   varchar(100) NOT NULL COMMENT '角色名称',
  `role_code`   varchar(100) NOT NULL COMMENT '角色编码（如 admin, teacher）',
  `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
  `status`      int(11)      DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `status`, `create_time`) VALUES
(1, '超级管理员', 'admin',   '系统超级管理员，拥有所有权限',       1, '2025-05-26 15:48:58'),
(2, '教师',       'teacher', '教师角色，管理考试和监考',           1, '2025-05-26 15:48:58'),
(3, '普通管理员', 'manager', '普通管理员，可管理系统配置',         1, '2025-05-26 15:48:58');

-- ----------------------------
-- 1.2 系统用户表（管理员与教师统一在此表，user_type 区分：0-管理员 1-教师）
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`    varchar(50)  NOT NULL COMMENT '用户名',
  `password`    varchar(200) NOT NULL COMMENT '密码',
  `real_name`   varchar(50)  DEFAULT NULL COMMENT '真实姓名',
  `email`       varchar(100) DEFAULT NULL COMMENT '电子邮箱',
  `phone`       varchar(20)  DEFAULT NULL COMMENT '联系电话',
  `avatar`      varchar(255) DEFAULT NULL COMMENT '头像URL',
  `user_type`   int(11)      DEFAULT '0' COMMENT '用户类型：0-管理员 1-教师',
  `status`      int(11)      DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` int(11)      DEFAULT NULL COMMENT '创建人ID',
  `update_user` int(11)      DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 默认密码均为 123456
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `email`, `phone`, `user_type`, `status`, `create_time`) VALUES
(1, 'admin',    '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '管理员',   'admin@ruikao.com',      '13700137001', 0, 1, '2025-12-03 19:20:41'),
(6, 'teacher1', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '王老师',   'teacher1@ruikao.com',   '13700137006', 1, 1, '2025-12-03 21:01:59'),
(8, 'admin1',   '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '历运志',   'admin1@ruikao.com',     '13700137008', 0, 1, '2025-12-04 08:41:53'),
(10,'teacher2', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '苏柳',     'teacher2@ruikao.com',   '13700137010', 1, 1, '2025-12-11 18:53:54');

-- ----------------------------
-- 1.3 用户角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` int(11) NOT NULL COMMENT '用户ID',
  `role_id` int(11) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1,  1),   -- admin  -> 超级管理员
(6,  2),   -- teacher1 -> 教师
(10, 2),   -- teacher2 -> 教师
(8,  1);   -- admin1 -> 超级管理员

-- ================================================================
-- 2. 人员管理
-- ================================================================

-- ----------------------------
-- 2.1 学生表
-- ----------------------------
DROP TABLE IF EXISTS `student`;
CREATE TABLE `student` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_no`  varchar(50)  NOT NULL COMMENT '学号',
  `password`    varchar(200) NOT NULL DEFAULT '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2' COMMENT '密码（默认123456）',
  `name`        varchar(50)  NOT NULL COMMENT '姓名',
  `gender`      varchar(10)  DEFAULT NULL COMMENT '性别',
  `major`       varchar(100) DEFAULT NULL COMMENT '专业',
  `grade`       varchar(50)  DEFAULT NULL COMMENT '年级',
  `class_name`  varchar(50)  DEFAULT NULL COMMENT '班级',
  `class_id`    int(11)      DEFAULT NULL COMMENT '关联班级ID（class_info.id）',
  `email`       varchar(100) DEFAULT NULL COMMENT '电子邮箱',
  `phone`       varchar(20)  DEFAULT NULL COMMENT '联系电话',
  `avatar`      varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status`      int(11)      DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `student_no` (`student_no`),
  KEY `idx_major` (`major`),
  KEY `idx_grade` (`grade`),
  KEY `idx_stu_class` (`class_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生信息表';

INSERT INTO `student` (`id`, `student_no`, `password`, `name`, `gender`, `major`, `grade`, `class_name`, `class_id`, `email`, `phone`, `status`, `create_time`) VALUES
(1, '202501', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '张三', '男', '计算机科学与技术', '2023级', '1班', 1, 'zhangsan@stu.ruikao.com', '13900139001', 1, '2025-09-01 08:00:00'),
(2, '202502', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '李四', '男', '软件工程',         '2022级', '2班', 2, 'lisi@stu.ruikao.com',    '13900139002', 1, '2025-09-01 08:00:00'),
(3, '202503', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '王五', '男', '计算机科学与技术', '2022级', '1班', 3, 'wangwu@stu.ruikao.com',  '13900139003', 1, '2025-09-01 08:00:00'),
(4, '202504', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '周六', '女', '数据科学与大数据', '2022级', '1班', 3, 'zhouliu@stu.ruikao.com', '13900139004', 1, '2025-09-01 08:00:00'),
(5, '202505', '$2a$10$6WOBpKQnsQ7JqtTLiDTFrOevw0CaB.FoIfZH9SniJXgW/Fo/ZMmN2', '陈九', '男', '软件工程',         '2024级', '1班', 4, 'chenjiu@stu.ruikao.com', '13900139005', 1, '2025-09-01 08:00:00');

-- ================================================================
-- 3. 考试安排
-- ================================================================

-- ----------------------------
-- 3.1 考试安排表
-- ----------------------------
DROP TABLE IF EXISTS `exam`;
CREATE TABLE `exam` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exam_name`   varchar(255) NOT NULL COMMENT '考试名称',
  `exam_type`   tinyint(4)   NOT NULL DEFAULT 0 COMMENT '考试类型：0-正式考试 1-课后作业',
  `course_name` varchar(100) DEFAULT NULL COMMENT '课程名称',
  `exam_date`   date         NOT NULL COMMENT '考试日期（作业为截止日期）',
  `start_time`  time         NOT NULL COMMENT '开始时间',
  `end_time`    time         NOT NULL COMMENT '结束时间',
  `duration`    int(11)      DEFAULT '0' COMMENT '考试时长（分钟）',
  `exam_room`   varchar(100) DEFAULT NULL COMMENT '考场（课后作业可为空）',
  `max_students` int(11)     DEFAULT '0' COMMENT '最大考生数',
  `paper_id`    int(11)      DEFAULT NULL COMMENT '关联试卷ID（开考必须关联试卷）',
  `creator_id`  int(11)      NOT NULL COMMENT '创建人ID（sys_user.id）',
  `status`      int(11)      DEFAULT '0' COMMENT '状态：0-未开始 1-进行中 2-已结束',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user` int(11)      DEFAULT NULL COMMENT '创建人ID',
  `update_user` int(11)      DEFAULT NULL COMMENT '修改人ID',
  PRIMARY KEY (`id`),
  KEY `idx_exam_date` (`exam_date`),
  KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试安排表';

INSERT INTO `exam` (`id`, `exam_name`, `course_name`, `exam_date`, `start_time`, `end_time`, `duration`, `exam_room`, `max_students`, `paper_id`, `creator_id`, `status`, `create_time`) VALUES
(2, '大学物理测试',     '大学物理', '2025-11-28', '21:58:00', '23:58:00', 120, '327',               30, 2,   1, 2, '2025-12-03 19:58:15'),
(3, '高等数学期中考试', '高等数学', '2025-12-11', '19:53:00', '20:53:00', 60,  '教学楼A101',        50, 1,   1, 2, '2025-12-11 17:53:35'),
(4, '大学语文测试',     '大学语文', '2025-12-05', '19:01:00', '22:01:00', 180, '3号教学楼326室',    40, 4,   1, 2, '2025-12-11 19:01:38'),
(5, '绘画',             '美术鉴赏', '2026-06-23', '20:43:00', '21:43:00', 60,  '3号教学楼326室',    30, NULL, 1, 2, '2026-06-21 16:43:20'),
(6, '线性代数期末测试', '线性代数', '2026-06-26', '12:10:00', '14:10:00', 120, '教学楼A101',        45, 3,   1, 2, '2026-06-21 18:10:43');

-- ================================================================
-- 4. 在线考试功能
-- ================================================================

-- ----------------------------
-- 4.1 题库表
-- ----------------------------
DROP TABLE IF EXISTS `question_bank`;
CREATE TABLE `question_bank` (
  `id`               INT         NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `question_type`    TINYINT     NOT NULL DEFAULT 0 COMMENT '题目类型：0-单选题 1-多选题 2-判断题 3-主观文字题 4-单空填空题 5-操作题',
  `question_content` TEXT        NOT NULL COMMENT '题目内容（支持HTML/富文本）',
  `options`          TEXT        NULL COMMENT '选项（JSON格式）',
  `answer`           VARCHAR(500) NOT NULL COMMENT '正确答案',
  `analysis`         TEXT        NULL COMMENT '答案解析',
  `score`            DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '默认分值',
  `difficulty`       TINYINT     NOT NULL DEFAULT 1 COMMENT '难度：1-简单 2-中等 3-困难',
  `subject_id`       INT         DEFAULT NULL COMMENT '所属科目ID（subject.id）',
  `creator_id`       INT         NOT NULL COMMENT '创建人（sys_user.id）',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_qb_type` (`question_type`),
  KEY `idx_qb_creator` (`creator_id`),
  KEY `idx_qb_difficulty` (`difficulty`),
  KEY `idx_qb_subject` (`subject_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

-- 高等数学（创建人：admin1，id=8）
INSERT INTO `question_bank` (`id`, `question_type`, `question_content`, `options`, `answer`, `score`, `difficulty`, `subject_id`, `creator_id`, `create_time`) VALUES
(1, 0, '函数 f(x)=x² 的导数是？',
    '["A. x", "B. 2x", "C. 2", "D. x²"]',
    'B', 5.0, 1, 1, 8, '2025-10-01 10:00:00'),(2, 0, 'lim(x→0) sin(x)/x = ?',
    '["A. 0", "B. 1", "C. ∞", "D. 不存在"]',
    'B', 5.0, 1, 1, 8, '2025-10-01 10:00:00'),(3, 0, '∫(0→1) x dx = ?',
    '["A. 0", "B. 0.5", "C. 1", "D. 2"]',
    'B', 5.0, 1, 1, 8, '2025-10-01 10:00:00'),(4, 0, '矩阵 [[1,2],[3,4]] 的行列式是？',
    '["A. -2", "B. 2", "C. 10", "D. -10"]',
    'A', 5.0, 2, 1, 8, '2025-10-01 10:00:00'),(5, 0, '曲线 y=x³ 在 x=1 处的切线斜率是？',
    '["A. 1", "B. 2", "C. 3", "D. 0"]',
    'C', 5.0, 2, 1, 8, '2025-10-01 10:00:00'),(6, 1, '以下哪些是奇函数？（多选）',
    '["A. sin(x)", "B. cos(x)", "C. x³", "D. tan(x)"]',
    'ACD', 8.0, 2, 1, 8, '2025-10-01 10:00:00'),(7, 2, '定积分表示的是曲线与x轴围成的面积。',
    NULL, '√', 3.0, 1, 1, 8, '2025-10-01 10:00:00'),(8, 2, '任意两个矩阵都可以相乘。',
    NULL, '×', 3.0, 1, 1, 8, '2025-10-01 10:00:00'),(9, 3, '请简述微积分基本定理的内容。',
    NULL, '微积分基本定理指出：如果函数f在[a,b]上连续，则F(x)=∫(a→x) f(t)dt在[a,b]上可导，且F\'(x)=f(x)。同时，∫(a→b) f(x)dx = F(b)-F(a)，其中F是f的任意一个原函数。', 10.0, 3, 1, 8, '2025-10-01 10:00:00'),
-- 大学物理（创建人：teacher1 王老师，id=6）
(10, 0, '牛顿第一定律又称为？',
    '["A. 惯性定律", "B. 加速度定律", "C. 作用力反作用力定律", "D. 万有引力定律"]',
    'A', 5.0, 1, 2, 6, '2025-10-01 10:00:00'),(11, 0, '光的本质是？',
    '["A. 粒子", "B. 波", "C. 波粒二象性", "D. 以上都不对"]',
    'C', 5.0, 1, 2, 6, '2025-10-01 10:00:00'),(12, 0, '一个物体从静止开始自由下落，3秒后的速度约为？',
    '["A. 9.8 m/s", "B. 19.6 m/s", "C. 29.4 m/s", "D. 39.2 m/s"]',
    'C', 5.0, 2, 2, 6, '2025-10-01 10:00:00'),(13, 0, '电荷守恒定律表明，孤立系统的总电荷？',
    '["A. 不断增加", "B. 不断减少", "C. 保持不变", "D. 周期性变化"]',
    'C', 5.0, 1, 2, 6, '2025-10-01 10:00:00'),(14, 2, '在真空中，所有电磁波的速度都等于光速。',
    NULL, '√', 3.0, 1, 2, 6, '2025-10-01 10:00:00'),(15, 2, '温度越高的物体含有的热量越多。',
    NULL, '×', 3.0, 1, 2, 6, '2025-10-01 10:00:00'),
-- 线性代数（创建人：admin1，id=8）
(16, 0, '向量组线性无关的充要条件是？',
    '["A. 秩等于向量个数", "B. 秩小于向量个数", "C. 秩为零", "D. 秩等于维数"]',
    'A', 5.0, 2, 3, 8, '2025-10-01 10:00:00'),(17, 0, '设A为n阶方阵，下列哪个条件不能推出A可逆？',
    '["A. |A| ≠ 0", "B. r(A) = n", "C. A有零特征值", "D. A的列向量组线性无关"]',
    'C', 5.0, 3, 3, 8, '2025-10-01 10:00:00'),(18, 1, '以下哪些是向量空间R³的子空间？（多选）',
    '["A. {(x,y,z)|x+y+z=0}", "B. {(x,y,z)|x=0}", "C. {(x,y,z)|x=1}", "D. {(x,y,z)|z=0}"]',
    'ABD', 8.0, 3, 3, 8, '2025-10-01 10:00:00'),
-- 大学语文（创建人：teacher2 苏柳，id=10）
(19, 0, '《红楼梦》的作者是？',
    '["A. 罗贯中", "B. 施耐庵", "C. 曹雪芹", "D. 吴承恩"]',
    'C', 5.0, 1, 4, 10, '2025-10-01 10:00:00'),(20, 0, '"但愿人长久，千里共婵娟"出自哪位诗人？',
    '["A. 李白", "B. 杜甫", "C. 苏轼", "D. 白居易"]',
    'C', 5.0, 1, 4, 10, '2025-10-01 10:00:00'),(21, 0, '下列哪个不是鲁迅的作品？',
    '["A. 《狂人日记》", "B. 《朝花夕拾》", "C. 《家》", "D. 《阿Q正传》"]',
    'C', 5.0, 1, 4, 10, '2025-10-01 10:00:00'),(22, 3, '请简要分析《静夜思》的意境和艺术特色。',
    NULL, '《静夜思》通过"床前明月光，疑是地上霜"的视觉错觉，引出"举头望明月，低头思故乡"的思乡之情。全诗语言质朴自然，意境深远，以月光为媒介，将游子的思乡之情表达得含蓄而深刻，体现了李白诗歌清新自然的艺术风格。', 10.0, 2, 4, 10, '2025-10-01 10:00:00'),
-- 大学英语（创建人：teacher1 王老师，id=6）
(23, 0, 'What is the meaning of "abandon"?',
    '["A. 接受", "B. 放弃", "C. 到达", "D. 提升"]',
    'B', 5.0, 1, 5, 6, '2025-10-01 10:00:00'),(24, 0, 'Which sentence is grammatically correct?',
    '["A. He go to school", "B. She goes to school", "C. They goes to school", "D. He going school"]',
    'B', 5.0, 1, 5, 6, '2025-10-01 10:00:00'),(25, 1, 'Which of the following are countable nouns?',
    '["A. water", "B. book", "C. information", "D. apple"]',
    'BD', 8.0, 2, 5, 6, '2025-10-01 10:00:00'),
-- 中国近代史（创建人：teacher2 苏柳，id=10）
(26, 0, '辛亥革命发生在哪一年？',
    '["A. 1898年", "B. 1911年", "C. 1919年", "D. 1949年"]',
    'B', 5.0, 1, 6, 10, '2025-10-01 10:00:00'),(27, 0, '"五四运动"爆发的直接原因是？',
    '["A. 巴黎和会外交失败", "B. 清政府腐败", "C. 军阀混战", "D. 经济危机"]',
    'A', 5.0, 1, 6, 10, '2025-10-01 10:00:00'),(28, 2, '鸦片战争标志着中国近代史的开端。',
    NULL, '√', 3.0, 1, 6, 10, '2025-10-01 10:00:00');
-- ----------------------------
-- 4.2 试卷表
-- ----------------------------
DROP TABLE IF EXISTS `exam_paper`;
CREATE TABLE `exam_paper` (
  `id`          INT           NOT NULL AUTO_INCREMENT COMMENT '试卷ID',
  `paper_name`  VARCHAR(200)  NOT NULL COMMENT '试卷名称',
  `total_score` DECIMAL(5,1)  NOT NULL DEFAULT 0 COMMENT '总分',
  `duration`    INT           NOT NULL DEFAULT 0 COMMENT '考试时长（分钟）',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布',
  `creator_id`  INT           NOT NULL COMMENT '创建人（sys_user.id）',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ep_creator` (`creator_id`),
  KEY `idx_ep_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

INSERT INTO `exam_paper` (`id`, `paper_name`, `total_score`, `duration`, `status`, `creator_id`, `create_time`) VALUES
(1, '高等数学期中考试卷', 100.0, 120, 1, 8,  '2025-11-01 10:00:00'),
(2, '大学物理基础测试卷', 100.0, 120, 1, 6,  '2025-11-15 10:00:00'),
(3, '线性代数单元测试卷', 100.0, 90,  0, 8,  '2025-11-20 10:00:00'),
(4, '大学语文期末测试卷', 100.0, 120, 1, 10, '2025-11-25 10:00:00'),
(5, '大学英语期中考试卷', 100.0, 90,  1, 6,  '2025-11-10 10:00:00');

-- ----------------------------
-- 4.3 试卷题目关联表
-- ----------------------------
DROP TABLE IF EXISTS `paper_question`;
CREATE TABLE `paper_question` (
  `id`             INT           NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `paper_id`       INT           NOT NULL COMMENT '试卷ID',
  `question_id`    INT           NOT NULL COMMENT '题目ID',
  `question_score` DECIMAL(5,1)  NOT NULL DEFAULT 0 COMMENT '本题分值',
  `sort_order`     INT           NOT NULL DEFAULT 0 COMMENT '题目序号',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
  KEY `idx_pq_paper` (`paper_id`),
  KEY `idx_pq_question` (`question_id`),
  CONSTRAINT `fk_pq_paper`    FOREIGN KEY (`paper_id`)    REFERENCES `exam_paper`   (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pq_question` FOREIGN KEY (`question_id`) REFERENCES `question_bank`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目关联表';

-- 试卷1：高等数学（单选5题+多选1题+判断2题+简答1题 = 5*5+8+3*2+10 = 49 → 凑整到50）
INSERT INTO `paper_question` (`id`, `paper_id`, `question_id`, `question_score`, `sort_order`, `create_time`) VALUES
(1,  1, 1,  5.0, 1, '2025-11-01 10:00:00'),
(2,  1, 2,  5.0, 2, '2025-11-01 10:00:00'),
(3,  1, 3,  5.0, 3, '2025-11-01 10:00:00'),
(4,  1, 4,  5.0, 4, '2025-11-01 10:00:00'),
(5,  1, 5,  5.0, 5, '2025-11-01 10:00:00'),
(6,  1, 6,  8.0, 6, '2025-11-01 10:00:00'),
(7,  1, 7,  3.0, 7, '2025-11-01 10:00:00'),
(8,  1, 8,  3.0, 8, '2025-11-01 10:00:00'),
(9,  1, 9,  10.0, 9, '2025-11-01 10:00:00');

-- 试卷2：大学物理（单选4题+判断2题）
INSERT INTO `paper_question` (`id`, `paper_id`, `question_id`, `question_score`, `sort_order`, `create_time`) VALUES
(10, 2, 10, 5.0, 1, '2025-11-15 10:00:00'),
(11, 2, 11, 5.0, 2, '2025-11-15 10:00:00'),
(12, 2, 12, 5.0, 3, '2025-11-15 10:00:00'),
(13, 2, 13, 5.0, 4, '2025-11-15 10:00:00'),
(14, 2, 14, 3.0, 5, '2025-11-15 10:00:00'),
(15, 2, 15, 3.0, 6, '2025-11-15 10:00:00');

-- 试卷4：大学语文（单选3题+简答1题）
INSERT INTO `paper_question` (`id`, `paper_id`, `question_id`, `question_score`, `sort_order`, `create_time`) VALUES
(20, 4, 19, 5.0,  1, '2025-11-25 10:00:00'),
(21, 4, 20, 5.0,  2, '2025-11-25 10:00:00'),
(22, 4, 21, 5.0,  3, '2025-11-25 10:00:00'),
(23, 4, 22, 10.0, 4, '2025-11-25 10:00:00');

-- 试卷5：大学英语（单选2题+多选1题）
INSERT INTO `paper_question` (`id`, `paper_id`, `question_id`, `question_score`, `sort_order`, `create_time`) VALUES
(24, 5, 23, 5.0, 1, '2025-11-10 10:00:00'),
(25, 5, 24, 5.0, 2, '2025-11-10 10:00:00'),
(26, 5, 25, 8.0, 3, '2025-11-10 10:00:00');

-- ----------------------------
-- 4.4 考试学生分配表
-- ----------------------------
DROP TABLE IF EXISTS `exam_student`;
CREATE TABLE `exam_student` (
  `id`          int(11)   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `exam_id`     int(11)   NOT NULL COMMENT '考试ID',
  `student_id`  int(11)   NOT NULL COMMENT '学生ID',
  `create_time` datetime  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
  KEY `idx_exam_id` (`exam_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试学生分配表';

-- 与考试记录保持一致：有答卷记录的考试对应分配名单
INSERT INTO `exam_student` (`id`, `exam_id`, `student_id`, `create_time`) VALUES
(1, 2, 1, '2025-11-28 21:50:00'),   -- 大学物理测试：张三
(2, 2, 3, '2025-11-28 21:50:00'),   -- 大学物理测试：王五
(3, 3, 1, '2025-12-11 19:50:00'),   -- 高等数学期中考试：张三
(4, 3, 2, '2025-12-11 19:50:00'),   -- 高等数学期中考试：李四
(5, 3, 3, '2025-12-11 19:50:00'),   -- 高等数学期中考试：王五
(6, 4, 1, '2025-12-05 18:55:00'),   -- 大学语文测试：张三
(7, 4, 2, '2025-12-05 18:55:00'),   -- 大学语文测试：李四
(8, 4, 3, '2025-12-05 18:55:00');   -- 大学语文测试：王五

-- ----------------------------
-- 4.5 考试记录表
-- ----------------------------
DROP TABLE IF EXISTS `exam_record`;
CREATE TABLE `exam_record` (
  `id`               INT           NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `paper_id`         INT           NULL COMMENT '试卷ID（可空：考试未关联试卷的边界情况）',
  `student_id`       INT           NOT NULL COMMENT '考生ID',
  `exam_id`          INT           NULL COMMENT '考试安排ID（NULL 表示独立练习）',
  `start_time`       DATETIME      NULL COMMENT '开始答题时间',
  `submit_time`      DATETIME      NULL COMMENT '交卷时间',
  `score`            DECIMAL(5,1)  NULL COMMENT '最终得分（NULL表示未阅卷）',
  `objective_score`  DECIMAL(5,1)  NULL COMMENT '客观题得分',
  `subjective_score` DECIMAL(5,1)  NULL COMMENT '主观题得分',
  `status`           TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0-未开始 1-考试中 2-已交卷(待批改) 3-已批改(待审核) 4-已审核(成绩终态)',
  `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_er_paper` (`paper_id`),
  KEY `idx_er_student` (`student_id`),
  KEY `idx_er_exam` (`exam_id`),
  KEY `idx_er_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试记录表';

INSERT INTO `exam_record` (`id`, `paper_id`, `student_id`, `exam_id`, `start_time`, `submit_time`, `score`, `objective_score`, `subjective_score`, `status`, `create_time`) VALUES
(1, 1, 1, 3, '2025-12-11 19:53:00', '2025-12-11 20:35:00', 85.0, 75.0, 10.0, 4, '2025-12-11 19:50:00'),
(2, 1, 2, 3, '2025-12-11 19:53:00', '2025-12-11 20:45:00', 72.0, 62.0, 10.0, 4, '2025-12-11 19:50:00'),
(3, 1, 3, 3, '2025-12-11 19:53:00', '2025-12-11 20:50:00', 78.0, 68.0, 10.0, 4, '2025-12-11 19:50:00'),
(4, 2, 1, 2, '2025-11-28 21:58:00', '2025-11-28 22:30:00', 68.0, 62.0, 6.0,  4, '2025-11-28 21:55:00'),
(5, 2, 3, 2, '2025-11-28 21:58:00', '2025-11-28 22:50:00', 90.0, 84.0, 6.0,  4, '2025-11-28 21:55:00'),
(6, 4, 1, 4, '2025-12-05 19:01:00', '2025-12-05 20:30:00', 88.0, 78.0, 10.0, 4, '2025-12-05 18:58:00'),
(7, 4, 2, 4, '2025-12-05 19:01:00', '2025-12-05 21:00:00', 65.0, 55.0, 10.0, 4, '2025-12-05 18:58:00'),
(8, 4, 3, 4, '2025-12-05 19:01:00', '2025-12-05 20:45:00', 92.0, 82.0, 10.0, 4, '2025-12-05 18:58:00'),
(9, 5, 4, NULL, '2026-07-01 10:00:00', '2026-07-01 10:45:00', 76.0, 68.0, 8.0,  4, '2026-07-01 09:55:00'),
(10,5, 5, NULL, '2026-07-01 10:00:00', '2026-07-01 10:30:00', 82.0, 74.0, 8.0,  4, '2026-07-01 09:55:00');

-- ----------------------------
-- 4.6 考生答案表
-- ----------------------------
DROP TABLE IF EXISTS `exam_answer`;
CREATE TABLE `exam_answer` (
  `id`             INT           NOT NULL AUTO_INCREMENT COMMENT '答案ID',
  `record_id`      INT           NOT NULL COMMENT '考试记录ID',
  `question_id`    INT           NOT NULL COMMENT '题目ID',
  `answer_content` TEXT          NULL COMMENT '考生作答内容',
  `attachment_url` VARCHAR(500)  DEFAULT NULL COMMENT '附件URL（操作题作答附件）',
  `score`          DECIMAL(5,1)  NULL COMMENT '本题得分（NULL表示未阅卷）',
  `is_correct`     TINYINT(1)    NULL COMMENT '是否正确：0-错误 1-正确',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_record_question` (`record_id`, `question_id`),
  KEY `idx_ea_record` (`record_id`),
  KEY `idx_ea_question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考生答案表';

-- 记录1（张三 - 高等数学）：成绩85分
INSERT INTO `exam_answer` (`id`, `record_id`, `question_id`, `answer_content`, `score`, `is_correct`, `create_time`) VALUES
(1,  1, 1, 'B', 5.0, 1, '2025-12-11 20:35:00'),
(2,  1, 2, 'B', 5.0, 1, '2025-12-11 20:35:00'),
(3,  1, 3, 'B', 5.0, 1, '2025-12-11 20:35:00'),
(4,  1, 4, 'A', 5.0, 1, '2025-12-11 20:35:00'),
(5,  1, 5, 'C', 5.0, 1, '2025-12-11 20:35:00'),
(6,  1, 6, 'ACD', 8.0, 1, '2025-12-11 20:35:00'),
(7,  1, 7, '√', 3.0, 1, '2025-12-11 20:35:00'),
(8,  1, 8, '×', 3.0, 1, '2025-12-11 20:35:00'),
(9,  1, 9, '微积分基本定理建立了微分与积分的联系，描述了定积分与被积函数原函数之间的关系。', 10.0, 1, '2025-12-11 20:35:00');

-- 记录2（李四 - 高等数学）：成绩72分（第4题错、第6题多选漏选）
INSERT INTO `exam_answer` (`id`, `record_id`, `question_id`, `answer_content`, `score`, `is_correct`, `create_time`) VALUES
(10, 2, 1, 'B', 5.0, 1, '2025-12-11 20:45:00'),
(11, 2, 2, 'B', 5.0, 1, '2025-12-11 20:45:00'),
(12, 2, 3, 'B', 5.0, 1, '2025-12-11 20:45:00'),
(13, 2, 4, 'C', 0.0, 0, '2025-12-11 20:45:00'),
(14, 2, 5, 'C', 5.0, 1, '2025-12-11 20:45:00'),
(15, 2, 6, 'AC', 4.0, 0, '2025-12-11 20:45:00'),
(16, 2, 7, '√', 3.0, 1, '2025-12-11 20:45:00'),
(17, 2, 8, '×', 3.0, 1, '2025-12-11 20:45:00'),
(18, 2, 9, '微积分基本定理：如果函数f在区间上连续，则它的积分上限函数可导。', 8.0, 0, '2025-12-11 20:45:00');

-- ================================================================
-- 5. 日志管理
-- ================================================================

-- ----------------------------
-- 5.1 统一操作日志表（由 SysOperLogAspect 切面记录）
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
  `id`          int(10)     NOT NULL AUTO_INCREMENT,
  `module`      varchar(100) DEFAULT NULL COMMENT '操作模块',
  `type`        varchar(100) DEFAULT NULL COMMENT '操作类型',
  `description` varchar(500) DEFAULT NULL COMMENT '操作描述',
  `operator`    varchar(100) DEFAULT NULL COMMENT '操作人用户名',
  `oper_ip`     varchar(100) DEFAULT NULL COMMENT '请求IP',
  `oper_uri`    varchar(500) DEFAULT NULL COMMENT '请求URI',
  `result`      varchar(10)  DEFAULT 'success' COMMENT '操作结果',
  `cost_time`   bigint(20)   DEFAULT NULL COMMENT '耗时（毫秒）',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_sys_oper_log_time` (`create_time`),
  KEY `idx_sys_oper_log_user` (`operator`),
  KEY `idx_oper_type` (`type`),
  KEY `idx_oper_module` (`module`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一操作日志表（由 SysOperLogAspect 切面记录）';

INSERT INTO `sys_oper_log` (`id`, `module`, `type`, `description`, `operator`, `oper_ip`, `oper_uri`, `result`, `cost_time`, `create_time`) VALUES
(1,  '登录认证', '登录',     '管理员登录系统',                     'admin',    '127.0.0.1', '/api/admin/auth/login',   'success', 230, '2026-07-28 08:00:00'),
(2,  '考试管理', '查询',     '查询考试管理数据',                   'admin',    '127.0.0.1', '/api/admin/exam/data',    'success', 16,  '2026-07-28 08:05:00'),
(3,  '用户管理', '查询',     '查询用户管理数据',                   'admin',    '127.0.0.1', '/api/admin/user/data',    'success', 5,   '2026-07-28 08:05:30'),
(4,  '角色管理', '查询',     '查询角色管理数据',                   'admin',    '127.0.0.1', '/api/admin/role/data',    'success', 3,   '2026-07-28 08:06:00'),
(5,  '考试管理', '新增',     '新增考试安排"高等数学期末测试"',     'admin',    '127.0.0.1', '/api/admin/exam/add',     'success', 45,  '2026-07-28 08:15:00'),
(6,  '试卷管理', '发布',     '发布试卷"高等数学期中考试卷"',       'teacher1', '192.168.1.10', '/api/admin/paper/publish',  'success', 30, '2026-07-28 09:00:00'),
(7,  '试卷管理', '查询',     '查询试卷数据',                       'teacher1', '192.168.1.10', '/api/admin/paper/data',   'success', 8, '2026-07-28 09:05:00'),
(8,  '用户管理', '修改',     '修改用户信息（admin1）',             'admin1',   '192.168.1.20', '/api/admin/user/edit',   'success', 12, '2026-07-28 10:00:00'),
(9,  '学生管理', '导入',     '批量导入学生信息（5条）',            'admin',    '127.0.0.1', '/api/admin/student/import', 'success', 500, '2026-07-28 10:30:00'),
(10, '操作日志', '查询',     '查询操作日志数据',                   'admin',    '127.0.0.1', '/api/admin/oplog/data',   'success', 7,   '2026-07-28 10:30:00'),
(11, '统计管理', '查询',     '查看考试统计图表',                   'admin',    '127.0.0.1', '/api/admin/chart/exam',   'success', 22,  '2026-07-28 14:00:00'),
(12, '登录认证', '登录',     '教师登录系统',                       'teacher2', '10.0.0.5', '/api/admin/auth/login',    'success', 180, '2026-07-28 14:30:00'),
(13, '考试管理', '删除',     '删除考试安排（失败-有考生记录）',     'admin',    '127.0.0.1', '/api/admin/exam/delete',  'fail',    50,  '2026-07-28 15:00:00'),
(14, '用户管理', '查询',     '查询教师用户列表',                   'admin',    '127.0.0.1', '/api/admin/user/data',     'success', 6,   '2026-07-28 15:30:00');

-- ================================================================
-- 6. 业务扩展模块（v1.4）
-- ================================================================

-- ----------------------------
-- 6.1 科目表
-- ----------------------------
DROP TABLE IF EXISTS `subject`;
CREATE TABLE `subject` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '科目ID',
  `subject_name` varchar(100) NOT NULL COMMENT '科目名称',
  `description` varchar(255) DEFAULT NULL COMMENT '科目描述',
  `status`      tinyint(4)   DEFAULT '1' COMMENT '状态：1-启用 0-停用',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_name` (`subject_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='科目表';

INSERT INTO `subject` (`id`, `subject_name`, `description`) VALUES
(1, '高等数学',   '函数、极限、导数与积分'),
(2, '大学物理',   '力学、电磁学与光学'),
(3, '线性代数',   '矩阵、向量与线性方程组'),
(4, '大学语文',   '文学鉴赏与写作'),
(5, '大学英语',   '英语语言基础'),
(6, '中国近代史', '中国近代历史进程');

-- ----------------------------
-- 6.2 班级表
-- ----------------------------
DROP TABLE IF EXISTS `class_info`;
CREATE TABLE `class_info` (
  `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '班级ID',
  `class_name`  varchar(100) NOT NULL COMMENT '班级名称',
  `grade`       varchar(50) DEFAULT NULL COMMENT '年级',
  `creator_id`  int(11)     NOT NULL COMMENT '创建人（sys_user.id）',
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_grade` (`class_name`, `grade`),
  KEY `idx_ci_creator` (`creator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

INSERT INTO `class_info` (`id`, `class_name`, `grade`, `creator_id`) VALUES
(1, '1班', '2023级', 1),
(2, '2班', '2022级', 1),
(3, '1班', '2022级', 1),
(4, '1班', '2024级', 1);

-- ----------------------------
-- 6.3 知识点表
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_point`;
CREATE TABLE `knowledge_point` (
  `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `subject_id`  int(11)      NOT NULL COMMENT '所属科目ID（subject.id）',
  `name`        varchar(100) NOT NULL COMMENT '知识点名称',
  `creator_id`  int(11)      NOT NULL COMMENT '创建人（sys_user.id）',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_kp_subject` (`subject_id`),
  KEY `idx_kp_creator` (`creator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

INSERT INTO `knowledge_point` (`id`, `subject_id`, `name`, `creator_id`) VALUES
(1,  1, '导数与微分',   8),
(2,  1, '极限',         8),
(3,  1, '不定积分',     8),
(4,  1, '定积分',       8),
(5,  2, '牛顿力学',     6),
(6,  2, '电磁学',       6),
(7,  3, '矩阵与行列式', 8),
(8,  3, '向量空间',     8),
(9,  4, '古典诗词鉴赏', 10),
(10, 4, '现代文学',     10),
(11, 5, '词汇与语法',   6),
(12, 6, '辛亥革命',     10);

-- ----------------------------
-- 6.4 题目知识点关联表
-- ----------------------------
DROP TABLE IF EXISTS `question_knowledge_point`;
CREATE TABLE `question_knowledge_point` (
  `id`                int(11) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `question_id`       int(11) NOT NULL COMMENT '题目ID（question_bank.id）',
  `knowledge_point_id` int(11) NOT NULL COMMENT '知识点ID（knowledge_point.id）',
  `create_time`       datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_kp` (`question_id`, `knowledge_point_id`),
  KEY `idx_qkp_kp` (`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目知识点关联表';

INSERT INTO `question_knowledge_point` (`question_id`, `knowledge_point_id`) VALUES
(1, 1),(2, 2),(3, 3),(4, 7),(5, 1),(6, 8),(7, 4),(8, 7),(9, 4),
(10, 5),(11, 6),(12, 5),(13, 6),(14, 6),(15, 5),
(16, 8),(17, 7),(18, 8),
(19, 9),(20, 9),(21, 10),(22, 9),
(23, 11),(24, 11),(25, 11),
(26, 12),(27, 12),(28, 12);

-- ----------------------------
-- 6.5 学习资料表
-- ----------------------------
DROP TABLE IF EXISTS `learning_material`;
CREATE TABLE `learning_material` (
  `id`          int(11)       NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `title`       varchar(200)  NOT NULL COMMENT '资料标题',
  `description` varchar(500)  DEFAULT NULL COMMENT '资料简介',
  `file_url`    varchar(500)  NOT NULL COMMENT '文件URL',
  `file_name`   varchar(255)  DEFAULT NULL COMMENT '文件原始名称',
  `file_size`   bigint(20)    DEFAULT NULL COMMENT '文件大小（字节）',
  `cover_url`   varchar(500)  DEFAULT NULL COMMENT '封面图URL',
  `subject_id`  int(11)       DEFAULT NULL COMMENT '所属科目ID（可空）',
  `creator_id`  int(11)       NOT NULL COMMENT '发布教师（sys_user.id）',
  `status`      tinyint(4)    DEFAULT '1' COMMENT '状态：0-下架 1-发布',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_lm_creator` (`creator_id`),
  KEY `idx_lm_subject` (`subject_id`),
  KEY `idx_lm_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习资料表';

INSERT INTO `learning_material` (`id`, `title`, `description`, `file_url`, `file_name`, `subject_id`, `creator_id`, `status`) VALUES
(1, '高等数学期末复习提纲', '涵盖极限、导数、积分核心考点，附典型例题',
    'https://exam-pt2.oss-cn-beijing.aliyuncs.com/material/20260801/gaoshu.pdf', '高等数学期末复习提纲.pdf', 1, 8, 1),
(2, '大学物理实验报告模板', '实验报告标准格式模板，含填写说明',
    'https://exam-pt2.oss-cn-beijing.aliyuncs.com/material/20260801/wuli.docx', '大学物理实验报告模板.docx', 2, 6, 1);

-- ----------------------------
-- 6.6 校园资讯表
-- ----------------------------
DROP TABLE IF EXISTS `campus_news`;
CREATE TABLE `campus_news` (
  `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '资讯ID',
  `title`        varchar(200) NOT NULL COMMENT '资讯标题',
  `cover_image`  varchar(500) DEFAULT NULL COMMENT '封面图片URL（置顶轮播用）',
  `content`      text         COMMENT '资讯正文（支持HTML）',
  `is_top`       tinyint(4)   DEFAULT '0' COMMENT '是否置顶轮播：0-普通 1-置顶',
  `status`       tinyint(4)   DEFAULT '0' COMMENT '状态：0-草稿 1-已发布',
  `creator_id`   int(11)      NOT NULL COMMENT '发布人（sys_user.id）',
  `publish_time` datetime     DEFAULT NULL COMMENT '发布时间',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cn_status` (`status`),
  KEY `idx_cn_istop` (`is_top`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='校园资讯表';

INSERT INTO `campus_news` (`id`, `title`, `cover_image`, `content`, `is_top`, `status`, `creator_id`, `publish_time`) VALUES
(1, '2026年秋季学期期末考试安排公告',
    'https://exam-pt2.oss-cn-beijing.aliyuncs.com/news/20260805/kaoshi.png',
    '<p>本学期期末考试将于2026年8月20日至8月25日举行，具体科目时间安排请以教务系统发布为准。</p><p>请各位同学合理安排复习时间，携带学生证准时参加考试。</p>',
    1, 1, 1, '2026-08-01 09:00:00'),
(2, '图书馆暑期开放时间调整通知',
    NULL,
    '<p>图书馆暑期开放时间为每日 8:00-18:00，周末不闭馆。电子资源 24 小时开放访问。</p>',
    0, 1, 1, '2026-08-02 09:00:00');

-- ----------------------------
-- 6.7 统一评论表（资讯/资料详情页共用）
-- ----------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id`          int(11)       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `biz_type`    varchar(50)   NOT NULL COMMENT '业务类型：NEWS-资讯 MATERIAL-学习资料',
  `biz_id`      int(11)       NOT NULL COMMENT '业务ID',
  `parent_id`   int(11)       DEFAULT NULL COMMENT '父评论ID（NULL为顶层评论）',
  `user_type`   tinyint(4)    DEFAULT '0' COMMENT '用户类型：0-管理员/教师（sys_user） 1-学生（student）',
  `user_id`     int(11)       NOT NULL COMMENT '用户ID',
  `user_name`   varchar(50)   NOT NULL COMMENT '用户昵称快照',
  `avatar`      varchar(500)  DEFAULT NULL COMMENT '头像URL快照',
  `content`     varchar(1000) NOT NULL COMMENT '评论内容',
  `status`      tinyint(4)    DEFAULT '1' COMMENT '状态：1-正常 0-已删除（软删）',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_cmt_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一评论表';

INSERT INTO `comment` (`biz_type`, `biz_id`, `parent_id`, `user_type`, `user_id`, `user_name`, `content`) VALUES
('NEWS', 1, NULL, 1, 1, '张三', '请问期末考试的具体时间表什么时候发布？'),
('NEWS', 1, 1, 0, 1, '管理员', '预计下周发布，请关注后续公告。');

-- ================================================================
-- 完成
-- ================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- ================================================================
-- 导出查看（导入完成后可执行以下语句查看各表数据量）
-- ================================================================
-- SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.tables
-- WHERE TABLE_SCHEMA = 'exam_system' ORDER BY TABLE_NAME;