package com.ruikao.common.constant;

/**
 * 考试系统业务常量，统一收口散落的魔法值
 */
public class ExamConstants {

    // ---------- 考试状态 ----------
    public static final int STATUS_NOT_STARTED = 0;
    public static final int STATUS_IN_PROGRESS = 1;
    public static final int STATUS_FINISHED = 2;

    // ---------- 考试记录状态 ----------
    public static final int RECORD_STATUS_EXAMINING = 1;   // 考试中（已开考未交卷）
    public static final int RECORD_STATUS_SUBMITTED = 2;   // 已交卷（待阅卷）
    public static final int RECORD_STATUS_FINALIZED = 3;   // 已定稿（成绩终态）

    // ---------- 题型 ----------
    public static final int QUESTION_TYPE_SINGLE = 0;      // 单选题
    public static final int QUESTION_TYPE_MULTIPLE = 1;    // 多选题
    public static final int QUESTION_TYPE_JUDGE = 2;       // 判断题
    public static final int QUESTION_TYPE_SUBJECTIVE = 3;  // 简答题（主观题）

    // ---------- 及格线（分） ----------
    public static final int PASS_LINE = 60;

    // ---------- 系统用户类型 ----------
    public static final int USER_TYPE_ADMIN = 0;
    public static final int USER_TYPE_TEACHER = 1;

    // ---------- 账号状态 ----------
    public static final int USER_STATUS_DISABLED = 0;
    public static final int USER_STATUS_ENABLED = 1;
}