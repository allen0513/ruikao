package com.ruikao.server.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ruikao.common.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 统一处理所有实体的 createTime / updateTime / createUser / updateUser 自动赋值
 * <p>
 * 实体中对应字段需标注：
 * - {@code @TableField(fill = FieldFill.INSERT)}        — 插入时自动填充
 * - {@code @TableField(fill = FieldFill.INSERT_UPDATE)} — 插入和更新时自动填充
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("MyBatis-Plus insertFill: {}", metaObject.getOriginalObject().getClass().getSimpleName());

        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        Long currentId = BaseContext.getCurrentId();
        if (currentId != null) {
            this.strictInsertFill(metaObject, "createUser", Long.class, currentId);
            this.strictInsertFill(metaObject, "updateUser", Long.class, currentId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("MyBatis-Plus updateFill: {}", metaObject.getOriginalObject().getClass().getSimpleName());

        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        Long currentId = BaseContext.getCurrentId();
        if (currentId != null) {
            this.strictUpdateFill(metaObject, "updateUser", Long.class, currentId);
        }
    }
}