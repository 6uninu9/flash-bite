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
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务，用于推送消息给客户端
 */
@Slf4j
@Component
// @ServerEndpoint注解用于定义websocket的接口地址，value属性指定接口地址
// 访问地址：http://xxxx/ws/merchantId
@ServerEndpoint(value = "/ws/{merchantId}")
public class WebSocketServer {

    // 存放会话对象
    // 使用ConcurrentHashMap的原因在于：
    //  - 线程安全：ConcurrentHashMap是线程安全的，可以多线程并发访问。
    // 使用嵌套ConcurrentHashMap的原因在于：
    //  - 在单店模式下，所有员工的 merchant_id 都是 1。
    //  - 如果 3 个员工同时登录，原来的 ConcurrentHashMap<String, Session> 会导致后面的 Session 覆盖前面的。
    //  - 所以使用嵌套 ConcurrentHashMap，每个员工对应一个 ConcurrentHashMap（外层是merchantId），存储该员工的所有会话（内层是sessionId）。
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Session>> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     * 将当前会话以 sid 为 key 存入全局映射表。
     *
     * @param session    WebSocket 会话对象，由容器自动注入
     * @param merchantId 路径参数，客户端的唯一标识（前端通过 URL 传入）
     */
    @OnOpen // @OnOpen注解用于标识连接建立成功时调用的方法
    public void onOpen(Session session, @PathParam("merchantId") String merchantId) {
        if (session == null || merchantId == null) {
            log.warn("OnOpen 收到无效参数，session={}, merchantId={}", session, merchantId);
            return;
        }
        // 存储会话，后续发送消息时根据 sid 获取
        SESSION_MAP.computeIfAbsent(merchantId, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        log.info("客户端merchantId=[{}]连接成功，sessionId=[{}]，当前在线会话数：{}", merchantId, session.getId(), SESSION_MAP.size());
    }

    /**
     * 当 WebSocket 连接关闭时被调用。
     * 从全局映射表中移除该客户端对应的会话。
     *
     * @param merchantId 路径参数，客户端的唯一标识
     */
    @OnClose // @OnClose注解用于标识连接关闭时调用的方法
    public void onClose(Session session, @PathParam("merchantId") String merchantId) {
        removeSession(merchantId, session.getId());
        log.info("客户端merchantId=[{}]断开连接，sessionId=[{}]，当前在线会话数：{}", merchantId, session.getId(), SESSION_MAP.size());
    }

    /**
     * 当 WebSocket 连接发生异常时被调用。
     * 通常表示网络故障或客户端非正常关闭，此时清理会话映射。
     *
     * @param merchantId 路径参数，客户端的唯一标识
     * @param error      异常对象，记录错误详情
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("merchantId") String merchantId) {
        // 1. 记录错误日志
        log.error("WebSocket 发生异常，sessionId={}, merchantId={}",
                session.getId(), merchantId, error);

        // 2. 移除无效会话
        removeSession(merchantId, session.getId());
    }

    private void removeSession(String merchantId, String sessionId) {
        if (merchantId == null || sessionId == null) {
            return;
        }
        ConcurrentHashMap<String, Session> sessions = SESSION_MAP.get(merchantId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                SESSION_MAP.remove(merchantId);
            }
        }
    }

    /**
     * 广播消息给所有客户端
     *
     * @param message 要发送的消息
     */
    public static void sendToAllClient(String message) {
        SESSION_MAP.values().forEach(sessions -> {
            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.getBasicRemote().sendText(message);
                    }
                } catch (IOException e) {
                    log.error("向会话 sessionId={} 发送消息失败：{}", session.getId(), e.getMessage(), e);
                }
            });
        });
    }

    /**
     * 发送消息给指定商家
     *
     * @param merchantId     路径参数，客户端的唯一标识
     * @param message 要发送的消息
     */
    public static void sendToUser(String merchantId, String message) {
        if (merchantId == null || message == null){
            log.warn("sendToUser 收到无效参数，merchantId={}, message={}", merchantId, message);
            return;
        }
        ConcurrentHashMap<String, Session> sessions = SESSION_MAP.get(merchantId);
        if (sessions == null || sessions.isEmpty()) {
            log.info("商家[{}]当前无在线会话，消息未发送", merchantId);
            return;
        }
        log.debug("开始向商家[{}]的 {} 个会话发送消息", merchantId, sessions.size());
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                    log.debug("消息已成功发送至会话 sessionId={}", session.getId());
                } else {
                    log.warn("会话 sessionId={} 已关闭，跳过发送", session.getId());
                }
            } catch (IOException e) {
                log.error("向会话 sessionId={} 发送消息失败：{}", session.getId(), e.getMessage(), e);
            }
        });
    }

}
