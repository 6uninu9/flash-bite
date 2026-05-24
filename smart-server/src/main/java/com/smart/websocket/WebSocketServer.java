package com.smart.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务，用于推送消息给客户端
 */
@Slf4j
@Component
// @ServerEndpoint注解用于定义websocket的接口地址，value属性指定接口地址
// 访问地址：http://xxxx/ws/sid
@ServerEndpoint(value ="/ws/{sid}")
public class WebSocketServer {

    // 存放会话对象
    // 使用ConcurrentHashMap的原因在于：
    //  - 线程安全：ConcurrentHashMap是线程安全的，可以多线程并发访问。
    private static final ConcurrentHashMap<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     * 将当前会话以 sid 为 key 存入全局映射表。
     *
     * @param session WebSocket 会话对象，由容器自动注入
     * @param sid 路径参数，客户端的唯一标识（前端通过 URL 传入）
     */
    @OnOpen // @OnOpen注解用于标识连接建立成功时调用的方法
    public void onOpen(Session session, @PathParam("sid") String sid) {
        // 存储会话，后续发送消息时根据 sid 获取
        SESSION_MAP.put(sid, session);
        log.info("客户端[{}]连接成功，当前在线会话数：{}", sid, SESSION_MAP.size());
    }

    /**
     * 当 WebSocket 连接关闭时被调用。
     * 从全局映射表中移除该客户端对应的会话。
     *
     * @param sid 路径参数，客户端的唯一标识
     */
    @OnClose // @OnClose注解用于标识连接关闭时调用的方法
    public void onClose(@PathParam("sid") String sid) {
        SESSION_MAP.remove(sid);
        log.info("客户端[{}]断开连接，剩余在线会话数：{}", sid, SESSION_MAP.size());
    }

    /**
     * 当 WebSocket 连接发生异常时被调用。
     * 通常表示网络故障或客户端非正常关闭，此时清理会话映射。
     *
     * @param sid   路径参数，客户端的唯一标识
     * @param error 异常对象，记录错误详情
     */
    @OnError
    public void onError(@PathParam("sid") String sid, Throwable error) {
        SESSION_MAP.remove(sid);
        log.error("客户端[{}]发生异常，即将移除会话。异常信息：{}", sid, error.getMessage(), error);
    }

    /**
     * 广播消息给所有客户端
     *
     * @param message 要发送的消息
     */
    public static void sendToAllClient(String message) {
        Collection<Session> sessions = SESSION_MAP.values();
        for (Session session : sessions) {
            try {
                // 服务器向客户端同步发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("服务端推送消息异常：{}", e.getMessage());
            }
        }
    }

    /**
     * 发送消息给指定用户
     *
     * @param sid 路径参数，客户端的唯一标识
     * @param message 要发送的消息
     */
    public static void sendToUser(String sid, String message) {
        // 1. 根据 sid 获取对应的会话
        Session session = SESSION_MAP.get(sid);
        if (session == null) {
            log.warn("向客户端[{}]发送消息失败：会话不存在（客户端可能未连接或已断开）", sid);
            return;
        }

        // 2. 校验会话是否仍然打开（网络正常且未主动关闭）
        if (!session.isOpen()) {
            log.warn("向客户端[{}]发送消息失败：会话已关闭", sid);
            SESSION_MAP.remove(sid); // 清理无效会话
            return;
        }

        // 3. 同步发送消息（阻塞直到消息发送完成或失败）
        //    相比异步发送，同步方式更可靠，能立即捕获发送异常；适用消息量不大但要求可靠性的场景（如订单提醒）。
        //    如果对性能要求极高，可改为 session.getAsyncRemote().sendText(message)，但需处理回调。
        try {
            session.getBasicRemote().sendText(message);
            log.debug("消息已成功发送至客户端[{}]：{}", sid, message);
        } catch (IOException e) {
            log.error("向客户端[{}]发送消息时发生IO异常：{}，消息内容：{}", sid, e.getMessage(), message, e);
            // 发生异常时通常会话已失效，从映射中移除
            SESSION_MAP.remove(sid);
        }
    }

}
