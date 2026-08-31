package com.ruikao.common.constant;

/**
 * 考试系统业务常量，统一收口散落的魔法值
 */
public class ExamConstants {

    // ---------- 考试状态 ----------
    public static final int STATUS_NOT_STARTED = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_FINISHED = 2;

    // ---------- 考试记录状态（阅卷 4 态） ----------
    public static final int RECORD_STATUS_EXAMINING = 1;   // 考试中（已开考未交卷）
    public static final int RECORD_STATUS_SUBMITTED = 2;   // 已交卷（待批改）
    public static final int RECORD_STATUS_MARKED = 3;      // 已批改（待教师审核确认）
    public static final int RECORD_STATUS_AUDITED = 4;     // 已审核（成绩终态）

    // ---------- 题型 ----------
    public static final int QUESTION_TYPE_SINGLE = 0;      // 单选题
    public static final int QUESTION_TYPE_MULTIPLE = 1;    // 多选题
    public static final int QUESTION_TYPE_JUDGE = 2;       // 判断题
    public static final int QUESTION_TYPE_SUBJECTIVE = 3;  // 简答题（主观题，人工批改）
    public static final int QUESTION_TYPE_FILL_BLANK = 4;  // 单空填空题（自动批改：trim 精确比对）
    public static final int QUESTION_TYPE_OPERATION = 5;   // 操作题（人工批改，附件上传）

    // ---------- 题目难度 ----------
    public static final int QUESTION_DIFFICULTY_EASY = 1;    // 简单
    public static final int QUESTION_DIFFICULTY_MEDIUM = 2;  // 中等
    public static final int QUESTION_DIFFICULTY_HARD = 3;    // 困难

    // ---------- 考试类型 ----------
    public static final int EXAM_TYPE_OFFICIAL = 0;  // 正式考试
    public static final int EXAM_TYPE_HOMEWORK = 1;  // 课后作业

    // ---------- 练习模式 ----------
    public static final String PRACTICE_MODE_FREE = "FREE";        // 自由刷题
    public static final String PRACTICE_MODE_SPECIAL = "SPECIAL";  // 专项练习
    public static final String PRACTICE_MODE_WRONG = "WRONG";      // 错题重做

    // ---------- 评论业务类型 ----------
    public static final String COMMENT_BIZ_NEWS = "NEWS";
    public static final String COMMENT_BIZ_MATERIAL = "MATERIAL";

    // ---------- 及格线（分） ----------
    public static final int PASS_LINE = 60;

    // ---------- 系统用户类型 ----------
    public static final int USER_TYPE_ADMIN = 0;
    public static final int USER_TYPE_TEACHER = 1;

    // ---------- 账号状态 ----------
    public static final int USER_STATUS_ENABLED = 1;
}