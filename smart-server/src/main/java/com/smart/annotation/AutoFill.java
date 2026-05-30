package com.smart.annotation;

import com.smart.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于标识要拦截以实现自动填充的方法
 */
// 1. @Target：指定注解能标注的位置（METHOD=方法，TYPE=类）
@Target(ElementType.METHOD) //用于方法
// 2. @Retention：指定注解保留到运行时（核心，否则反射读不到）
@Retention(RetentionPolicy.RUNTIME) //运行时执行
// 3. @Documented：生成文档时包含该注解（可选）
//@Documented
public @interface AutoFill {

    //指定数据库操作类型：UPDATE INSERT
    OperationType value();
}
