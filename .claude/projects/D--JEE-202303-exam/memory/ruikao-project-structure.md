---
name: ruikao-project-structure
description: 睿考平台多模块项目结构概览
metadata:
  type: reference
---

# 睿考平台 - 后端多模块架构

## 模块结构
```
ruikao/                              (父POM)
├── pom.xml                          # 父聚合工程, Spring Boot 3.2.5
├── ruikao-common/                   # 公共模块 (21 files)
│   └── com.ruikao.common/
│       ├── constant/                # JwtClaimsConstant, MessageConstant, ExamConstants
│       ├── context/                 # BaseContext (ThreadLocal)
│       ├── enumeration/             # OperationType, QuestionType
│       ├── exception/               # BaseException + 6个子类
│       ├── json/                    # JacksonObjectMapper
│       ├── properties/              # JwtProperties, ExamProperties
│       ├── result/                  # Result<T>, PageResult<T>
│       └── utils/                   # JwtUtil, PasswordUtil, IpUtil
├── ruikao-pojo/                     # POJO模块 (34 files)
│   └── com.ruikao.pojo/
│       ├── entity/                  # 15个实体 (SysUser, Exam, QuestionBank...)
│       ├── dto/                     # 12个请求DTO
│       └── vo/                      # 7个响应VO
└── ruikao-server/                   # 服务端 (76 files)
    └── com.ruikao.server/
        ├── annotation/              # AutoFill, OperLog, RequirePermission
        ├── aspect/                  # AutoFillAspect, OperLogAspect, PermissionAspect
        ├── config/                  # Cors, Knife4j, MyBatis-Plus, Redis, WebMvc, WebSocket
        ├── handler/                 # GlobalExceptionHandler
        ├── interceptor/             # JwtTokenAdminInterceptor, JwtTokenStudentInterceptor (双轨)
        ├── controller/
        │   ├── admin/               # 13个管理端控制器
        │   ├── student/             # 4个学生端控制器
        │   └── common/              # FileController (文件上传)
        ├── service/                 # 12个Service接口
        │   └── impl/                # 12个Service实现
        ├── mapper/                  # 15个MyBatis-Plus Mapper
        ├── websocket/               # ExamProctorWebSocket (监考实时推送)
        └── task/                    # ExamStatusTask, ProctorCleanupTask
```

## 关键设计
- **苍穹外卖架构风格**: 双JWT拦截器 + AOP自动填充 + AOP操作日志 + 统一返回Result
- **RBAC权限**: 5张权限表 (sys_user/role/permission + 关联表)
- **接口前缀**: /api/admin/** 和 /api/student/**, 双Knife4j分组
- **密码安全**: BCrypt加密
- **数据库**: ruikao_exam (MySQL)
- **缓存**: Redis + Spring Cache注解
- **实时监考**: WebSocket协议 (心跳/切屏上报/异常行为)

## 编译命令
`./mvnw clean compile` 或 `./mvnw clean install`