package com.smart.context;

/**
 * 基础上下文类，用于存储和管理线程局部变量，特别是一个 Long 类型的 ID 值。
 * 每个线程都有自己独立的变量副本，避免多线程环境下的变量共享问题。
 */
public class BaseContext {

    // 线程局部变量，用于存储 Long 类型的 ID 值（
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前线程的 Long 类型的 ID 值。
     *
     * @param id 要设置的 ID 值
     */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /**
     * 获取当前线程存储在 ThreadLocal 中的 Long 类型的 ID 值。
     *
     * @return 当前线程的 ID 值，如果 ThreadLocal 中没有存储值则返回 null
     */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /**
     * 移除当前线程在 ThreadLocal 中存储的 ID 值，防止内存泄漏。
     */
    public static void removeCurrentId() {
        threadLocal.remove();
    }
}
