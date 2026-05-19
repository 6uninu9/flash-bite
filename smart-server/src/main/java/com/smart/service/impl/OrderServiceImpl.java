package com.smart.service.impl;

import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.entity.*;
import com.smart.exception.AddressBookBusinessException;
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

    private final UserCouponMapper userCouponMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, AddressBookMapper addressBookMapper, ShoppingCartMapper shoppingCartMapper, RocketMQTemplate rocketMQTemplate, DishMapper dishMapper, CouponMapper couponMapper, Executor orderTaskExecutor, UserCouponMapper userCouponMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.addressBookMapper = addressBookMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
        this.couponMapper = couponMapper;
        this.orderTaskExecutor = orderTaskExecutor;
        this.userCouponMapper = userCouponMapper;
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

        // 1. 异步获取地址簿（工具类处理异常）
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

        // 2. 异步处理购物车和库存扣减
        CompletableFuture<List<ShoppingCart>> shoppingCartFuture = CompletableFutureUtil.supplyAsync(() -> {
            // 获取购物车数据
            ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
            List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
            // 判断购物车数据是否为空
            if (shoppingCarts == null || shoppingCarts.isEmpty()) {
                // 空则抛出异常
                throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
            }
            // 扣减库存
            shoppingCarts.forEach(cart -> {
                // 使用数据库行锁+乐观锁，避免并发问题和超卖
                dishMapper.deductStockByDishId(cart.getDishId(), cart.getNumber());
            });
            return shoppingCarts;
        }, orderTaskExecutor);

        // 3. 异步查询查询优惠卷获取扣减金额
        // 能够选择的优惠卷应该由前端筛选，所以这里只需计算前端传来的优惠卷id对应的优惠金额总和并返回即可
        CompletableFuture<BigDecimal> couponFuture = CompletableFutureUtil.supplyAsync(() -> {
            // 优惠卷id集合为空则返回0
            if (couponIds == null || couponIds.isEmpty()){
                return BigDecimal.ZERO;
            }
            // 获取优惠卷集合
            List<Coupon> coupons = couponMapper.selectBatchById(couponIds);
            // 获取优惠卷集合中的优惠金额并返回
            return coupons.stream() // 把优惠券列表转换成 Stream 流
                    .filter(Objects::nonNull) // 过滤出不为空的对象
                    .map(Coupon::getDiscountAmount) // 类型转换，把流里的每一个优惠券对象，转换成对应的优惠金额
                    .reduce(BigDecimal.ZERO,BigDecimal::add) // 聚合计算，把流里的多个数据合并成一个数据，即将BigDecimal.ZERO与其他优惠金额进行相加
                    .setScale(2, RoundingMode.HALF_UP); // 将计算出的总金额保留 2 位小数 + 四舍五入
        }, orderTaskExecutor);


        // 4. 等待所有异步任务完成（工具类统一处理异常）
        CompletableFutureUtil.allOf(addressBookFuture, shoppingCartFuture, couponFuture);

        // 5. 获取结果
        /*
          工具类中的allOf已经对并行任务的异常进行了统一处理
          所以执行到这里说明并行任务正常执行完毕，只需要等待完成并获取结果
          而get()带有checked异常，所以使用get()又要多余使用try-catch块捕获不存在的异常
          ，join()会抛出unchecked CompletionException，可以不捕获，所以使用join()，可以让代码更简洁
         */
        AddressBook addressBook = addressBookFuture.join();
        List<ShoppingCart> shoppingCarts = shoppingCartFuture.join();
        BigDecimal couponAmount = couponFuture.join();

        // 6. 插入订单数据
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

        // 7. 插入订单明细
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBath(orderDetails);

        // 8. 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 9. 发送延迟消息
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

        // 10. 返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(amount)
                .build();
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
     * @param number  订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderAndReleaseStock(String orderId, String number) {
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
