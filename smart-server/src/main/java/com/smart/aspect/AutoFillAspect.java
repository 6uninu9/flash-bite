package com.smart.aspect;

import com.smart.annotation.AutoFill;
import com.smart.constant.AutoFillConstant;
import com.smart.context.BaseContext;
import com.smart.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面类，用于拦截Mapper层用@AutoFill标识的方法，在其执行前进行自动填充
 */
@Slf4j
@Component
@Aspect
public class AutoFillAspect {

    /**
     * 切入点，用于划定拦截范围以及范围内的拦截对象
     */
    @Pointcut("execution(* com.smart.mapper.*.*(..)) && @annotation(com.smart.annotation.AutoFill)")
    public void autoFillPointCut(){}

    /**
     * 使用前置通知，将拦截到的方法，在其执行前在通知中完成自动填充
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        log.info("开始进行公共字段自动填充......");

        //获取当前被拦截到的方法上由@AutoFill标识的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();//获取方法签名
        AutoFill annotation = signature.getMethod().getAnnotation(AutoFill.class);//获得方法上的注解对象
        OperationType operationType = annotation.value();//获得数据库操作类型

        //获取当前被拦截到的方法的参数--实体对象
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0){//检查是否有参数，提高安全性
            return;
        }
        Object entity = args[0];//获取实体对象，一般默认第一个参数是要获取的实体对象

        //准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        //根据数据库操作类型，为对应属性通过反射的方式来赋值
        if (operationType == OperationType.INSERT){
            //获取拦截到方法的线程（实体对象）中对应的赋值方法
            Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
            Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

            //通过反射为对应的属性赋值
            setCreateTime.invoke(entity, now);
            setUpdateTime.invoke(entity, now);
            setCreateUser.invoke(entity, currentId);
            setUpdateUser.invoke(entity, currentId);
        }else if (operationType == OperationType.UPDATE){
            //获取拦截到方法的线程（实体对象）中对应的赋值方法
            Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

            //通过反射为对应的属性赋值
            setUpdateTime.invoke(entity, now);
            setUpdateUser.invoke(entity, currentId);
        }
    }
}
