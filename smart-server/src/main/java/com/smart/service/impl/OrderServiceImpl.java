package com.smart.service.impl;

import com.smart.constant.CacheKeyConstants;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.entity.*;
import com.smart.exception.AddressBookBusinessException;
import com.smart.exception.BaseException;
import com.smart.exception.DishBusinessException;
import com.smart.exception.ShoppingCartBusinessException;
import com.smart.mapper.*;
import com.smart.service.OrderService;
import com.smart.utils.CompletableFutureUtil;
import com.smart.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private final OrderDetailMapper orderDetailMapper;

    private final AddressBookMapper addressBookMapper;

    private final ShoppingCartMapper shoppingCartMapper;

    private final RocketMQTemplate rocketMQTemplate;

    private final DishMapper dishMapper;

    private final CouponMapper couponMapper;

    @Qualifier("orderTaskExecutor")
    private final Executor orderTaskExecutor;

    private final StringRedisTemplate stringRedisTemplate;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, AddressBookMapper addressBookMapper, ShoppingCartMapper shoppingCartMapper, RocketMQTemplate rocketMQTemplate, DishMapper dishMapper, CouponMapper couponMapper, Executor orderTaskExecutor, StringRedisTemplate stringRedisTemplate) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.addressBookMapper = addressBookMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
        this.couponMapper = couponMapper;
        this.orderTaskExecutor = orderTaskExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 下单数据
     * @return 订单确认数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 获取用户id
        Long userId = BaseContext.getCurrentId();
        // 获取优惠卷id集合
        List<Long> couponIds = ordersSubmitDTO.getCouponId();
        // 记录所有成功加锁的优惠券KEY的集合, 用于发生异常时释放占用锁
        List<String> lockedKeyList = new ArrayList<>();

        try {
            // 1. 判断使用的优惠卷有没有给其他订单使用，没有则给使用的优惠卷添加占用锁
            // 主要是为了解决 用户下单使用了优惠卷，但是未支付，而另下单使用相同的优惠卷导致最后支付发生的优惠卷使用冲突的问题
            // 在用户下单时占用使用的优惠卷 除非用户主动取消订单、用户支付超时系统自动取消订单、商家拒单、执行发生异常释放占用的优惠卷
            // 否则不能使用同一张优惠卷重复下单
            if (couponIds != null && !couponIds.isEmpty()) { // 优惠卷id集合不为空，即有使用优惠卷
                // 遍历优惠卷id集合
                couponIds.forEach(couponId -> {
                    // 构建优惠卷占用锁键
                    String couponLockKey = CacheKeyConstants.LOCK_COUPON_OCCUPY_KEY + userId + ":" + couponId;
                    // 尝试获取锁
                    Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(couponLockKey, "", 32, TimeUnit.MINUTES);
                    // 判断是否获取锁成功
                    if (Boolean.FALSE.equals(lock)) {
                        // 获取锁失败
                        throw new BaseException(MessageConstant.COUPON_OCCUPIED_BY_OTHER_ORDER);
                    }
                    lockedKeyList.add(couponLockKey);
                });
            }

            // 2. 异步获取地址簿（工具类处理异常）
            CompletableFuture<AddressBook> addressBookFuture = CompletableFutureUtil.supplyAsync(() -> {
                // 获取地址簿
                AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
                // 判断地址簿是否为空
                if (addressBook == null) {
                    // 空则抛出异常
                    throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
                }
                return addressBook;
            }, orderTaskExecutor);

            // 3. 异步查询购物车
            CompletableFuture<List<ShoppingCart>> shoppingCartFuture = CompletableFutureUtil.supplyAsync(() -> {
                // 获取购物车数据
                ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
                List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
                // 判断购物车数据是否为空
                if (shoppingCarts == null || shoppingCarts.isEmpty()) {
                    // 空则抛出异常
                    throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
                }
                // 遍历购物车数据查询对应的菜品数据
                shoppingCarts.forEach(shCart -> {
                    // 获取菜品
                    Dish dish = dishMapper.getById(shCart.getDishId());
                    // 判断菜品是否为空或者状态是否为停售
                    if (dish == null|| Objects.equals(dish.getStatus(), Dish.DISABLE)){
                        // 空或者停售则抛出异常 终止下单
                        throw new DishBusinessException(MessageConstant.DISH_IS_NOT_AVAILABLE);
                    }
                });
                return shoppingCarts;
            }, orderTaskExecutor);

            // 4. 异步查询查询优惠卷获取扣减金额
            // 能够选择的优惠卷应该由前端筛选，所以这里只需计算前端传来的优惠卷id对应的优惠金额总和并返回即可
            CompletableFuture<BigDecimal> couponFuture = CompletableFutureUtil.supplyAsync(() -> {
                // 优惠卷id集合为空则返回0
                if (couponIds == null || couponIds.isEmpty()) {
                    return BigDecimal.ZERO;
                }
                // 获取优惠卷集合
                List<Coupon> coupons = couponMapper.selectBatchById(couponIds);
                // 获取优惠卷集合中的优惠金额并返回
                return coupons.stream() // 把优惠券列表转换成 Stream 流
                        .filter(Objects::nonNull) // 过滤出不为空的对象
                        .map(Coupon::getDiscountAmount) // 类型转换，把流里的每一个优惠券对象，转换成对应的优惠金额
                        .reduce(BigDecimal.ZERO, BigDecimal::add) // 聚合计算，把流里的多个数据合并成一个数据，即将BigDecimal.ZERO与其他优惠金额进行相加
                        .setScale(2, RoundingMode.HALF_UP); // 将计算出的总金额保留 2 位小数 + 四舍五入
            }, orderTaskExecutor);

            // 5. 等待所有异步任务完成（工具类统一处理异常）
            CompletableFutureUtil.allOf(addressBookFuture, shoppingCartFuture, couponFuture);

            // 6. 获取结果
            /*
              工具类中的allOf已经对并行任务的异常进行了统一处理
              所以执行到这里说明并行任务正常执行完毕，只需要等待完成并获取结果
              而get()带有checked异常，所以使用get()又要多余使用try-catch块捕获不存在的异常
              ，join()会抛出unchecked CompletionException，可以不捕获，所以使用join()，可以让代码更简洁
             */
            AddressBook addressBook = addressBookFuture.join();
            List<ShoppingCart> shoppingCarts = shoppingCartFuture.join();
            BigDecimal couponAmount = couponFuture.join();

            // 7. 插入订单数据
            Orders orders = new Orders();
            BeanUtils.copyProperties(ordersSubmitDTO, orders);
            BigDecimal amount = ordersSubmitDTO.getAmount().subtract(couponAmount);
            orders.setOrderTime(LocalDateTime.now());
            orders.setPayStatus(Orders.UN_PAID);
            orders.setStatus(Orders.PENDING_PAYMENT);
            orders.setAmount(amount);
            orders.setNumber(String.valueOf(System.currentTimeMillis()) + userId);
            orders.setPhone(addressBook.getPhone());
            orders.setConsignee(addressBook.getConsignee());
            orders.setAddress(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
            orders.setUserId(userId);
            orderMapper.insert(orders);

            // 8. 插入订单明细
            List<OrderDetail> orderDetails = new ArrayList<>();
            for (ShoppingCart cart : shoppingCarts) {
                OrderDetail orderDetail = new OrderDetail();
                BeanUtils.copyProperties(cart, orderDetail);
                orderDetail.setOrderId(orders.getId());
                orderDetails.add(orderDetail);
            }
            orderDetailMapper.insertBath(orderDetails);

            // 9. 扣减库存
            // 将扣减库存提取到主线程，因为异步线程脱离事务，发生异常无法回滚
            // 在下单时就扣减库存 主要是因为:
            // 用户选完一堆菜品，填完地址，跳到支付页，却发现菜品已售空，用户体验极差，极易流失。
            // 除此之外 这里的扣减库存只是预扣减(用户超时未支付后释放库存) 后厨拿到单子进行出餐是在用户支付后
            shoppingCarts.forEach(cart -> {
                // 扣减库存并获取扣减后影响的行数用于判断是否扣减成功
                // 使用数据库行锁+乐观锁，避免并发问题和超卖
                int rows = dishMapper.deductStockByDishId(cart.getDishId(), cart.getNumber());
                if (rows == 0) {
                    throw new BaseException(cart.getName() + "-" + MessageConstant.DISH_SOLD_OUT);
                }
            });

            // 10. 清空购物车
            shoppingCartMapper.deleteByUserId(userId);

            // 11. 发送延迟消息
            Message<String> message = MessageBuilder.withPayload(orders.getNumber() + "-" + orders.getId()).build();
            rocketMQTemplate.asyncSend("orderTopic", message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("发送取消订单延迟消息成功");
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("发送取消订单延迟消息失败");
                }
            }, 30000, 16);

            // 12. 返回结果
            return OrderSubmitVO.builder()
                    .id(orders.getId())
                    .orderNumber(orders.getNumber())
                    .orderTime(orders.getOrderTime())
                    .orderAmount(amount)
                    .build();
        } catch (Exception e) {
            // 发生异常释放优惠卷占用锁
            lockedKeyList.forEach(stringRedisTemplate::delete);
            // 将异常向上抛出 让事务回滚
            throw e;
        }
    }

    /**
     * 获取订单信息
     *
     * @param orderId 订单id
     * @return 订单信息
     */
    @Override
    public Orders getOrderById(Long orderId) {
        return orderMapper.getById(orderId);
    }

    /**
     * 取消订单并释放库存
     *
     * @param orderId 订单id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderAndReleaseStock(String orderId) {
        // 1. 取消订单
        Orders orders = Orders.builder()
                .id(Long.valueOf(orderId))
                .status(Orders.CANCELLED)
                .cancelReason("系统自动取消")
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        // 2. 释放库存
        // 2.1. 查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(Long.valueOf(orderId));
        // 2.2. 遍历明细，逐一释放菜品库存
        orderDetails.forEach(orderDetail -> {
            // 获取菜品
            Dish dish = dishMapper.getById(orderDetail.getDishId());
            // 释放库存
            Dish newDish = Dish.builder().id(orderDetail.getDishId())
                    .stock(dish.getStock() + orderDetail.getNumber())
                    .updateTime(LocalDateTime.now())
                    .updateUser(0L)
                    .build();
            dishMapper.update(newDish);
        });
    }
}
