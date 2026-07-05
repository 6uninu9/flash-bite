package com.smart.listener.mq;

import com.smart.constant.CacheTimeConstant;
import com.smart.entity.Orders;
import com.smart.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 订单取消 MQ 消息监听器
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "orderTopic", // 订阅的主题
        consumerGroup = "order-consumer-group", // 消费者组
        consumeMode = ConsumeMode.CONCURRENTLY, // 消费模式 并发消费
        consumeThreadNumber = 32 // 并发消费线程数 处理器*2
)
public class CancelOrderListener implements RocketMQListener<MessageExt> {

    private final OrderService orderService;

    private final RedissonClient redissonClient;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDENTITY_KEY = "idempotent:order:cancel:";

    private static final String LOCK_KEY = "lock:cancelOrder:" ;

    public CancelOrderListener(OrderService orderService, RedissonClient redissonClient, StringRedisTemplate stringRedisTemplate) {
        this.orderService = orderService;
        this.redissonClient = redissonClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        // 1. 提取订单编号和订单id
        String msg = null;
        long number;
        Long orderId;

        try {
            byte[] body = messageExt.getBody();
            if (body == null || body.length == 0) {
                log.error("消息体为空，无法解析。");
                return;
            }
            msg = new String(body, StandardCharsets.UTF_8);

            String[] parts = msg.split("-", 2);
            if (parts.length < 2) {
                log.error("消息格式错误，缺少分隔符'-'。原始消息：{}", msg);
                return;
            }
            number = Long.parseLong(parts[0].trim());
            orderId = Long.valueOf(parts[1].trim());

        } catch (NumberFormatException e) {
            log.error("消息中ID格式错误，解析失败。原始消息：{}", msg, e);
            return;
        }

        log.info("收到取消订单的延迟消息：{}", msg);

        // 2. 消息幂等性校验 避免消息重复消费
        // 使用redis的setnx 命令进行去重判断，key相同的为重复消息
        // TTL设置时长要覆盖同一条消息还可能被重复投递的整个时间窗口
        // 因为使用默认的重复投递次数16，考虑到极端情况应该设置时间>5h覆盖重试所需的时间
        // 又考虑到可能会发生消费者 GC 暂停、网络抖动、Broker 重平衡等造成的额外延迟，所以设置TTL为6h
        if (Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(IDENTITY_KEY + number, "", CacheTimeConstant.DUPLICATE_CHECK_TTL_SECONDS, TimeUnit.SECONDS))) {
            log.warn("取消订单消息重复");
            return;
        }

        // 3. 获取订单信息，判断订单是否支持取消
        Orders orders = orderService.getOrderById(orderId);

        // 3.1. 订单不存在
        if (orders == null) {
            log.error("订单不存在");
            return;
        }
        // 3.2. 订单不是待付款状态，不能取消
        if (orders.getStatus() > 1) {
            log.error("订单不是待付款状态，不能取消");
            return;
        }

        // 需要加锁 因为释放mysql库存时需要查询计算再修改，不然就使用数据库行锁

        // 4. 创建锁
        RLock lock = redissonClient.getLock(LOCK_KEY + orderId);

        boolean locked = false;
        try {
            // 5. 尝试获取锁，最多等待3秒，使用看门狗机制 除非发生异常不然锁自动续期
            locked = lock.tryLock(3, TimeUnit.SECONDS);

            // 6. 如果获取锁失败
            if (!locked) {
                log.warn("获取锁超时，触发重试: orderId={}", orderId);
                // 抛出错误让RocketMQ重试
                throw new RuntimeException("获取锁失败");
            }

            // 7. 修改订单状态为取消 并释放库存
            orderService.cancelOrderAndReleaseStock(orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 报错 则删除幂等标识
            stringRedisTemplate.delete(IDENTITY_KEY + number);
            throw new RuntimeException("线程中断", e);
        } catch (Exception e) { // 如果mysql取消订单释放库存异常 则不会继续往下执行
            log.error("取消订单失败", e);
            // 报错 则删除幂等标识
            stringRedisTemplate.delete(IDENTITY_KEY + number);
            throw new RuntimeException("取消订单失败", e);
        } finally {
            // 8. 释放锁
            // 让只有当前线程才释放锁
            // 因为10秒后锁自动释放，锁给其他线程获取到，此时当前线程 locked=true，但锁已不是自己的，释放的是别人的锁。
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
