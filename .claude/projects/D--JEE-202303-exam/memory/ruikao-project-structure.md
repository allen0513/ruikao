---
name: ruikao-project-structure
description: 在线考试管理系统（原"睿考"）后端单体项目结构概览
metadata:
  type: reference
---

# 在线考试管理系统（原"睿考"）- 后端单体架构

## 项目结构（单模块 Maven）
```
ruikao/                              (单一模块, Spring Boot 3.5.14, Java 17)
├── pom.xml                          # 唯一 pom, jar 打包, spring-boot-maven-plugin
├── src/main/java/com/ruikao/
│   ├── common/                      # 公共层 (16 files, 原 ruikao-common)
│   │   ├── constant/                # ExamConstants, JwtClaimsConstant
│   │   ├── context/                 # BaseContext (ThreadLocal)
│   │   ├── exception/               # BaseException + 4个子类
│   │   ├── properties/              # JwtProperties, AliOssProperties, WeChatProperties
│   │   ├── result/                  # Result<T>, PageResult<T>
│   │   └── utils/                   # JwtUtil, PasswordUtil, IpUtil, WeChatUtil
│   ├── pojo/                        # 数据载体 (原 ruikao-pojo)
│   │   ├── entity/                  # 17个实体 (v1.4 新增: Subject, ClassInfo, KnowledgePoint,
│   │   │                            #   QuestionKnowledgePoint, LearningMaterial, CampusNews, Comment)
│   │   ├── dto/                     # 26个请求DTO (含 AutoPaperDTO, PracticeStartDTO, CommentDTO)
│   │   └── vo/                      # 17个响应VO (含 WrongQuestionVO, PracticeRecordVO, MaterialVO)
│   └── server/                      # 业务层 (原 ruikao-server)
│       ├── RuiKaoApplication.java   # 启动类, scanBasePackages="com.ruikao"
│       ├── annotation/              # OperLog, RedisLock
│       ├── aspect/                  # OperLogAspect, RedisLockAspect
│       ├── config/                  # Cors, Knife4j, Redis, WebMvc, WebSocket
│       ├── handler/                 # GlobalExceptionHandler, MyMetaObjectHandler
│       ├── interceptor/             # CommonJwt, JwtTokenAdmin, JwtTokenStudent (双轨)
│       ├── controller/
│       │   ├── admin/               # 管理端控制器 (含 News/Material/Comment 管理)
│       │   ├── student/             # 学生端控制器 (含 Practice 练习)
│       │   └── common/              # FileController + 前台 News/Material/Comment
│       ├── service/ + impl/         # Service接口与实现 (v1.4 新增 Practice/News/Comment/LearningMaterial)
│       ├── mapper/                  # 19个MyBatis-Plus Mapper
│       ├── security/                # IpRateLimit, LoginAttempt, TokenBlacklist
│       ├── websocket/               # WebSocketServer (待阅卷提醒推送)
│       └── task/                    # ExamStatusTask
├── src/main/resources/              # application.yml(+dev), logback, sql/exam_system.sql + migration_v1.4_business_modules.sql
└── src/test/java/                   # 30个单元测试
```

## v1.4 业务扩展（2026-08-05 完成，见 docs/业务开发文档.md）
- **字典模块**: 科目/班级/知识点 CRUD + 教师归属过滤（creator_id 校验）
- **题库扩展**: 题型 4 单空填空（自动批改 trim 比对）、5 操作题（附件 attachment_url）；subjectId/analysis/知识点多选（question_knowledge_point 事务维护）
- **自动组卷**: POST /api/admin/paper/auto-generate（AutoPaperDTO 按 题型×难度 随机抽题，库存不足抛中文异常）
- **防作弊题序**: startExam 随机打乱写 Redis `exam:order:{recordId}`（TTL=剩余时长），刷新同序，异常回退 sortOrder
- **阅卷4态**: 1考试中→2已交卷(自动批客观题0/1/2/4)→3已批改(complete)→4已审核(confirm 写排行榜ZSET+清chart缓存)；成绩仅 status=4 学生端可见
- **考试类型**: exam.examType 0正式/1作业；/assign-by-class 按班级批量分配（去重+RedisLock）
- **习题学习**: /api/student/practice/start(FREE/SPECIAL/WRONG) + submit(练习直达4) + wrong-question/page 错题集 + records；练习记录 exam_id/paper_id 均 NULL
- **校园资讯+评论**: /api/admin/news CRUD + /api/common/news/banner|page|{id}；/api/common/comment 发布（CommonJwtInterceptor 透传 userType 区分 sys_user/student 命名空间）+ 软删
- **学习资料**: /api/admin/material（教师归属过滤）+ /api/common/material/page（仅已发布）
- **遗留**: 前端 steps 3-8 页面（练习页/资讯/资料/CommentPanel/自动组卷对话框等）待开发

## v1.5 性能与运维增强（2026-08-16）
- **题库缓存**: QuestionBankServiceImpl.getDetail 加 @Cacheable("questionBank", key=id)，update/delete 按 id 驱逐（TTL 默认 60s）
- **Excel 批量导入题库**: POST /api/admin/question/import（multipart），模板 9 列「题型|题目内容|选项|答案|解析|分值|难度|科目ID|知识点ID」，题型支持 0-5 或中文名，逐行校验+事务批量入库，返回 QuestionImportResultVO（成功数+逐行错误）；@RedisLock 防重复导入
- **异步生成成绩统计**: AsyncConfig（@EnableAsync + ThreadPoolTaskExecutor chartAsyncExecutor，CallerRunsPolicy）+ ChartServiceImpl.refreshCacheAsync 异步预热 chart 缓存；成绩定稿/删除（ExamRecordServiceImpl）事务提交后 afterCommit 异步刷新，ExamStatusTask 状态流转后异步刷新
- 测试：QuestionBankServiceImplTest（7 用例覆盖导入解析/校验/错误汇总）

## 关键设计
- **苍穹外卖架构风格**: 双JWT拦截器 + AOP自动填充 + AOP操作日志 + 统一返回Result
- **RBAC权限**: sys_user/role/user_role 关联表
- **接口前缀**: /api/admin/** 和 /api/student/**, 双Knife4j分组
- **密码安全**: BCrypt加密 (spring-security-crypto)
- **数据库**: exam_system (MySQL)
- **缓存**: Redis + Spring Cache注解
- **实时推送**: WebSocket (学生交卷 → 管理端待阅卷提醒)

## 编译命令
`./mvnw clean test` 或 `./mvnw clean package`