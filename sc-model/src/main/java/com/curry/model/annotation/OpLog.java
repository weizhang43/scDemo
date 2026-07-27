package com.curry.model.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上，由共享的 OperationLogAspect 拦截并记录操作日志到 t_operation_log。
 * 放在 sc-model 供所有业务模块引用。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 所属模块，如"角色管理" */
    String module() default "";

    /** 操作类型 */
    OpType type() default OpType.OTHER;

    /** 操作描述，如"新增角色" */
    String description() default "";

    enum OpType {
        QUERY, ADD, UPDATE, DELETE, LOGIN, EXPORT, OTHER
    }
}
