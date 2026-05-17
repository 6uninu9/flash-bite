package com.smart.utils;

import com.smart.constant.MessageConstant;
import com.smart.exception.SystemException;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 异步工具类
 */
public class CompletableFutureUtil {

    // 默认超时时间，单位毫秒
    private static final int DEFAULT_TIMEOUT_MS = 3000;

    /**
     * 异步执行一个任务，并统一处理超时异常和异常解包。
     * <p>
     * 该方法会在指定的线程池中异步执行 supplier 提供的任务，同时设置超时时间。
     * 如果任务在超时时间内正常完成，则返回的 CompletableFuture 会包含任务的结果；
     * 如果超时，会将 TimeoutException 转换为 SystemException 抛出；
     * 如果任务内部抛出了业务异常（如 AddressBookBusinessException）或系统异常，
     * 则会直接抛出原始异常类型（解包后），而不是包装成 ExecutionException。
     * <p>
     * 这样做的好处是：调用方可以通过 .exceptionally 或 .join() 捕获到原始的异常类型，
     * 便于全局异常处理器区分业务异常和系统异常，同时避免多层包装带来的麻烦。
     *
     * @param supplier   异步任务的逻辑（可以是 Lambda 表达式，包含业务代码）
     * @param executor   执行异步任务的线程池（建议根据业务隔离自定义线程池）
     * @param timeoutMs  超时时间，单位毫秒（若任务执行超过此时间，则触发超时异常）
     * @param <T>        异步任务返回结果的类型
     * @return CompletableFuture 包装了异步任务的执行结果，但异常已经经过解包和转换
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier,
                                                        Executor executor,
                                                        long timeoutMs) {
        return CompletableFuture
                // 1. 在指定线程池中异步执行 supplier 任务
                .supplyAsync(supplier, executor)
                // 2. 设置超时：如果任务在 timeoutMs 毫秒内未完成，则触发 TimeoutException
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                // 3. 处理任何异常（包括超时、业务异常、系统异常等）
                .exceptionally(e -> {
                    // 3.1 解包 CompletionException
                    //    由于 supplyAsync 内部抛出的异常会被包装成 CompletionException，
                    //    我们需要获取其 cause 才能拿到真正的异常对象。
                    Throwable cause = (e instanceof CompletionException) ? e.getCause() : e;

                    // 3.2 如果真正的原因是超时异常，则转换为自定义的系统异常 SystemException
                    //    这样上层可以统一处理超时场景（例如返回 "系统繁忙，请稍后重试"）
                    if (cause instanceof TimeoutException) {
                        throw new SystemException(MessageConstant.ASYNC_TIMEOUT,cause);
                    }

                    // 3.3 如果 cause 已经是 RuntimeException（或其子类），则直接原样抛出；
                    //    否则（理论上不会发生，因为我们的业务代码只抛 RuntimeException）包装为 RuntimeException。
                    //    这样做的目的是让调用方能够捕获到原始的业务异常（如 AddressBookBusinessException），
                    //    而不需要从 ExecutionException 中层层解包。
                    //    其中(RuntimeException)在三元运算中必须强制转型，否则返回的异常类型不一致（cause为Throwable）
                    //    但是如果if判断是可以省略的，可以直接抛出
                    throw (cause instanceof RuntimeException) ? (RuntimeException) cause : new RuntimeException(cause);
                });
    }

    // 使用默认超时
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        return supplyAsync(supplier, executor, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 等待多个异步任务全部完成，并在任一任务失败时抛出原始异常。
     * <p>
     * 此方法会阻塞当前线程，直到所有传入的 CompletableFuture 都执行完毕。
     * 如果所有任务都成功完成，方法正常返回；如果至少一个任务抛出了异常，
     * 本方法会解开 ExecutionException 包装，直接抛出原始的异常对象。
     * <p>
     * 这样做的目的是让调用方能够直接捕获到业务异常（如 AddressBookBusinessException），
     * 而不需要层层解包，同时保证事务能够按预期回滚。
     *
     */
    public static void allOf(CompletableFuture<?>... futures) {
        try {
            // 1. CompletableFuture.allOf() 创建一个新的 CompletableFuture，
            //    当所有给定的 futures 都完成时，这个新的 future 也完成。
            //    如果任何一个 future 异常完成，则这个新的 future 也会异常完成。
            // 2. .get() 会阻塞当前线程，等待所有任务完成。
            //    如果有任务抛出异常，.get() 会抛出 ExecutionException。
            //    如果当前线程在等待期间被其他线程中断，则抛出 InterruptedException。
            CompletableFuture.allOf(futures).get();
        } catch (ExecutionException e) {
            // ExecutionException 是 .get() 抛出的 checked 异常，包装了异步任务内部的实际异常。
            // 例如：异步代码中 throw new AddressBookBusinessException(...)，
            // 则 e.getCause() 就是那个 AddressBookBusinessException 对象。
            Throwable cause = e.getCause();

            // 由于我们的异步任务中所有业务异常和系统异常都已经是 RuntimeException 的子类，
            // 因此如果 cause 是 RuntimeException，直接原样抛出，这样调用方可以用精准的异常类型捕获。
            // 如果不是 RuntimeException（理论上不会发生，但作为防御性代码保留），则包装成 RuntimeException 再抛出。
            throw (cause instanceof RuntimeException) ? (RuntimeException) cause : new RuntimeException(cause);
        } catch (InterruptedException e) {
            // InterruptedException 表示当前线程在等待时被其他线程中断。
            // 为了保持中断状态（便于上层代码知晓中断请求），需要重新设置中断标志。
            Thread.currentThread().interrupt();

            // 抛出系统异常，表示异步任务被中断，订单流程无法继续。
            // 这里不直接抛出 InterruptedException，因为它是 checked 异常，
            // 而我们的方法签名没有 throws InterruptedException，所以用 SystemException 包装。
            throw new SystemException("任务被中断", e);
        }
    }
}