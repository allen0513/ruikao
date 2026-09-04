# 睿考 Ruikao · 在线考试管理系统

> Smart Exam, Fair Proctor.

睿考（Ruikao）是一个基于 **Spring Boot 3 + JDK 17** 的在线考试管理系统后端服务，采用经典三层架构（Controller - Service - Mapper），按角色划分为**管理端**、**学生端**与**公共接口**，覆盖题库管理、试卷组卷、考试安排、在线答题、自动/人工阅卷、成绩统计、刷题练习等完整的考试业务闭环。

---

## 目录

- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [功能模块](#功能模块)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [接口文档](#接口文档)
- [数据库设计](#数据库设计)
- [默认账号](#默认账号)
- [项目结构](#项目结构)
- [构建与部署](#构建与部署)

---

## 核心特性

- 🔐 **双端 JWT 认证**：管理端与学生端使用独立密钥隔离签发，配合 Redis Token 黑名单实现登出即失效
- 🛡️ **登录安全防护**：连续失败 5 次锁定账号 15 分钟，密码采用 BCrypt 加密存储
- 📝 **智能组卷与阅卷**：支持自动组卷，客观题（单选/多选/判断/填空）自动评分，主观题人工批改 + 教师审核的 4 态阅卷流程
- ⏱️ **考试状态自动流转**：定时任务每分钟扫描，自动切换「未开始 / 进行中 / 已结束」并对已结束考试的未交卷答卷强制收卷
- 📊 **成绩统计与图表**：Dashboard 数据看板、排行榜、成绩分布等统计，配合缓存异步刷新
- 🔌 **实时消息推送**：基于 WebSocket 的学生交卷 → 管理端待阅卷提醒
- 📤 **文件云端存储**：学习资料、操作题附件等上传至阿里云 OSS
- 📄 **Excel 成绩导出**：基于 Apache POI 导出成绩单
- 📚 **扩展业务**：题库知识点关联、刷题练习（自由/专项/错题）、学习资料、校园资讯、评论互动
- 🧾 **操作日志与分布式锁**：AOP 记录关键操作日志，Redis 分布式锁保障并发安全

---

## 技术栈

| 分类 | 技术选型 | 版本 |
| --- | --- | --- |
| 基础框架 | Spring Boot | 3.5.14 |
| 运行环境 | JDK | 17 |
| 持久层 | MyBatis-Plus | 3.5.9 |
| 分页插件 | PageHelper | 2.1.0 |
| 数据库连接池 | Druid | 1.2.23 |
| 数据库 | MySQL | 8.x |
| 缓存 / 分布式锁 | Redis (Lettuce) | - |
| 认证 | JWT (jjwt) | 0.12.6 |
| 密码加密 | Spring Security Crypto (BCrypt) | - |
| 实时通信 | WebSocket | - |
| 对象存储 | 阿里云 OSS SDK | 3.17.4 |
| Excel 处理 | Apache POI | 5.4.0 |
| 接口文档 | Knife4j + SpringDoc OpenAPI | 4.5.0 / 2.8.17 |
| 简化代码 | Lombok | - |
| 单元测试 | JUnit 5 + Mockito + AssertJ | - |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                      客户端 (Web / App)                    │
└─────────────────────────────────────────────────────────┘
                           │  HTTP / WebSocket
┌──────────────────────────▼──────────────────────────────┐
│  Controller 层  (admin / student / common)                │
│    ├─ JWT 拦截器：JwtTokenAdmin / Student / Common        │
│    ├─ AOP：操作日志 (OperLogAspect) / 分布式锁 (RedisLock) │
│    └─ 全局异常处理 (GlobalExceptionHandler)               │
├──────────────────────────────────────────────────────────┤
│  Service 层  (业务逻辑 / 事务 / 缓存 / 自动阅卷)           │
├──────────────────────────────────────────────────────────┤
│  Mapper 层  (MyBatis-Plus)                                │
└──────────────────────────┬──────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
     MySQL              Redis            阿里云 OSS
   (业务数据)      (缓存 / 锁 / 黑名单)    (文件存储)

   定时任务 (ExamStatusTask)：考试状态流转 + 自动收卷
   WebSocket (/ws/{sid})：待阅卷消息推送
```

---

## 功能模块

### 管理端 `/api/admin/**`

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 登录认证 | `/api/admin/auth` | 登录、登出、获取当前用户信息 |
| 考试管理 | `/api/admin/exam` | 考试安排、发布、按班级指派 |
| 试卷管理 | `/api/admin/paper` | 组卷、试卷题目配置 |
| 题库管理 | `/api/admin/question` | 题目录入、批量导入、知识点关联 |
| 阅卷管理 | `/api/admin/record` | 答卷批改、审核、成绩管理 |
| 学生管理 | `/api/admin/student` | 学生信息 CRUD |
| 班级管理 | `/api/admin/class` | 班级信息 CRUD |
| 用户管理 | `/api/admin/user` | 管理员/教师账号管理 |
| 角色管理 | `/api/admin/role` | 角色与权限 |
| 科目管理 | `/api/admin/subject` | 学科分类 |
| 知识点管理 | `/api/admin/knowledge` | 知识点树 |
| 学习资料 | `/api/admin/material` | 资料上传与管理 |
| 校园资讯 | `/api/admin/news` | 资讯发布 |
| 评论管理 | `/api/admin/comment` | 评论审核 |
| 数据图表 | `/api/admin/chart` | Dashboard 统计数据 |
| 操作日志 | `/api/admin/log` | 系统操作日志查询 |

### 学生端 `/api/student/**`

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 登录认证 | `/api/student/auth` | 学生登录、登出 |
| 在线考试 | `/api/student/exam` | 考试列表、进入考试、交卷 |
| 答题 | `/api/student/answer` | 保存答案、查询答题记录 |
| 成绩查询 | `/api/student/record` | 个人考试记录与成绩 |
| 刷题练习 | `/api/student/practice` | 自由刷题、专项练习、错题重做 |
| 个人中心 | `/api/student/profile` | 个人信息维护 |

### 公共接口 `/api/common/**`

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 文件上传 | `/api/common` | 通用文件上传（OSS） |
| 科目查询 | `/api/common/subject` | 公共科目数据 |
| 知识点查询 | `/api/common/knowledge` | 公共知识点数据 |
| 学习资料 | `/api/common/material` | 资料浏览下载 |
| 校园资讯 | `/api/common/news` | 资讯浏览 |
| 评论互动 | `/api/common/comment` | 发表评论 |

> 公共接口需携带管理端或学生端任一有效 Token 访问。

---

## 快速开始

### 环境要求

- **JDK 17**
- **Maven 3.6+**（项目内置 Maven Wrapper，可直接使用 `./mvnw`）
- **MySQL 8.x**
- **Redis**（用于缓存、分布式锁、Token 黑名单）
- **阿里云 OSS**（文件上传，可选本地调试）

### 1. 克隆项目

```bash
git clone https://github.com/allen0513/ruikao.git
cd exam
```

### 2. 初始化数据库

创建数据库并导入初始化脚本（脚本位于 `src/main/resources/sql/exam_system.sql`）：

```sql
CREATE DATABASE IF NOT EXISTS `exam_system`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `exam_system`;
-- 执行 exam_system.sql 全部内容
```

### 3. 配置本地环境

复制配置模板并填入真实值（`application-dev.yml` 已被 `.gitignore` 忽略，禁止提交凭据）：

```bash
# 模板位置：ruikao-server/src/main/resources/application-dev.yml.example
# 复制到：src/main/resources/application-dev.yml
```

主要需配置数据库连接、Redis 地址、阿里云 OSS 凭证等，详见 [配置说明](#配置说明)。

### 4. 启动服务

```bash
# 方式一：Maven 运行
./mvnw spring-boot:run

# 方式二：IDEA 直接运行主类
# com.ruikao.server.RuiKaoApplication
```

启动成功后：

- 服务地址：<http://localhost:8080>
- 接口文档：<http://localhost:8080/doc.html>

---

## 配置说明

主配置 `src/main/resources/application.yml` 通过 `${ENV_VAR:default}` 形式支持环境变量注入，生产环境务必使用环境变量覆盖敏感默认值。

| 环境变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 激活的 Profile | `dev` |
| `MYSQL_PASSWORD` | 数据库密码 | `root` |
| `RUIKAO_REDIS_HOST` | Redis 主机 | 空（dev 由 application-dev.yml 提供） |
| `RUIKAO_REDIS_PORT` | Redis 端口 | `6379` |
| `RUIKAO_REDIS_PASSWORD` | Redis 密码 | 空 |
| `RUIKAO_REDIS_DATABASE` | Redis 库索引 | 空 |
| `RUIKAO_JWT_ADMIN_SECRET` | 管理端 JWT 密钥 | 本地开发默认值 |
| `RUIKAO_JWT_STUDENT_SECRET` | 学生端 JWT 密钥 | 本地开发默认值 |
| `RUIKAO_ALIOSS_ENDPOINT` | OSS Endpoint | 空 |
| `RUIKAO_ALIOSS_ACCESS_KEY_ID` | OSS AccessKeyId | 空 |
| `RUIKAO_ALIOSS_ACCESS_KEY_SECRET` | OSS AccessKeySecret | 空 |
| `RUIKAO_ALIOSS_BUCKET_NAME` | OSS Bucket | 空 |
| `RUIKAO_CORS_ALLOWED_ORIGINS` | 允许跨域来源（逗号分隔） | `*` |

> ⚠️ **安全提示**：生产环境必须通过环境变量注入独立的 JWT 密钥、数据库密码、Redis 密码与 OSS 凭证，并将 `RUIKAO_CORS_ALLOWED_ORIGINS` 收紧为具体域名。生产环境（`prod` Profile）下接口文档 `/doc.html` 不会加载。

---

## 接口文档

项目集成 **Knife4j**（基于 SpringDoc OpenAPI 3），启动后访问：

- 文档地址：<http://localhost:8080/doc.html>
- 接口按分组展示：**管理端接口** / **学生端接口** / **公共接口**

---

## 数据库设计

数据库 `exam_system`，字符集 `utf8mb4`，共 **19 张表**：

| 分类 | 数据表 |
| --- | --- |
| 系统管理 | `sys_role`、`sys_user`、`sys_user_role` |
| 人员管理 | `student`、`class_info` |
| 考试安排 | `exam` |
| 在线考试 | `question_bank`、`exam_paper`、`paper_question`、`exam_student`、`exam_record`、`exam_answer` |
| 业务扩展 | `subject`、`knowledge_point`、`question_knowledge_point`、`learning_material`、`campus_news`、`comment` |
| 操作日志 | `sys_oper_log` |

**核心状态模型：**

- 考试状态：`0`-未开始 / `1`-进行中 / `2`-已结束
- 阅卷状态（4 态）：`1`-考试中 / `2`-已交卷（待批改）/ `3`-已批改（待审核）/ `4`-已审核（成绩终态）
- 题型：`0`-单选 / `1`-多选 / `2`-判断 / `3`-主观题 / `4`-单空填空 / `5`-操作题

---

## 默认账号

初始化脚本内置以下测试账号，**密码均为 `123456`**：

| 角色 | 账号 | 说明 |
| --- | --- | --- |
| 超级管理员 | `admin` | 拥有全部权限 |
| 管理员 | `admin1` | 超级管理员 |
| 教师 | `teacher1` / `teacher2` | 管理考试与监考 |
| 学生 | `202501` ~ `202505` | 学号登录 |

> ⚠️ 默认账号仅用于本地开发测试，生产部署后请立即修改密码。

---

## 项目结构

```
exam/
├── src/main/java/com/ruikao/
│   ├── common/                     # 公共组件
│   │   ├── constant/               # 业务常量（ExamConstants、JwtClaimsConstant）
│   │   ├── context/                # 线程上下文（BaseContext）
│   │   ├── exception/              # 自定义异常
│   │   ├── properties/             # 配置属性（JwtProperties、AliOssProperties）
│   │   ├── result/                 # 统一返回结果（Result、PageResult）
│   │   └── utils/                  # 工具类（JwtUtil、PasswordUtil、IpUtil）
│   ├── pojo/
│   │   ├── dto/                    # 请求参数对象
│   │   ├── entity/                 # 数据库实体
│   │   └── vo/                     # 视图返回对象
│   └── server/
│       ├── annotation/             # 自定义注解（@OperLog、@RedisLock）
│       ├── aspect/                 # AOP 切面（操作日志、分布式锁）
│       ├── config/                 # 配置类（WebMvc、Redis、CORS、Knife4j、WebSocket、Async）
│       ├── controller/             # 控制层（admin / student / common）
│       ├── handler/                # 处理器（全局异常、字段自动填充）
│       ├── interceptor/            # JWT 拦截器
│       ├── mapper/                 # MyBatis-Plus Mapper
│       ├── security/               # 安全组件（登录限流、Token 黑名单）
│       ├── service/                # 业务层（接口 + impl）
│       ├── task/                   # 定时任务（考试状态流转）
│       ├── websocket/              # WebSocket 服务
│       └── RuiKaoApplication.java  # 启动类
├── src/main/resources/
│   ├── sql/exam_system.sql         # 数据库初始化脚本
│   ├── application.yml             # 主配置
│   ├── application-dev.yml         # 本地开发配置（gitignore）
│   └── logback-spring.xml          # 日志配置
├── src/test/java/                  # 单元测试
└── pom.xml
```

---

## 构建与部署

### 打包

```bash
./mvnw clean package -DskipTests
```

生成产物：`target/ruikao-1.0.0.jar`

### 运行

```bash
# 使用默认 dev profile
java -jar target/ruikao-1.0.0.jar

# 指定生产 profile 并通过环境变量注入敏感配置
export SPRING_PROFILES_ACTIVE=prod
export RUIKAO_JWT_ADMIN_SECRET=your-admin-secret
export RUIKAO_JWT_STUDENT_SECRET=your-student-secret
export MYSQL_PASSWORD=your-db-password
java -jar target/ruikao-1.0.0.jar
```

### 运行测试

```bash
./mvnw test
```

---

## License

本项目仅用于学习与交流。
