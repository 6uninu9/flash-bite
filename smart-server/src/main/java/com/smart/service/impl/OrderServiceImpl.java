package com.smart.service.impl;

import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.entity.*;
import com.smart.exception.AddressBookBusinessException;
import com.smart.exception.DishBusinessException;
import com.smart.exception.ShoppingCartBusinessException;
import com.smart.mapper.*;
import com.smart.service.OrderService;
import com.smart.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private final OrderDetailMapper orderDetailMapper;

    private final AddressBookMapper addressBookMapper;

    private final ShoppingCartMapper shoppingCartMapper;

    private final RocketMQTemplate rocketMQTemplate;

    private final DishMapper dishMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, AddressBookMapper addressBookMapper, ShoppingCartMapper shoppingCartMapper, RocketMQTemplate rocketMQTemplate, DishMapper dishMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.addressBookMapper = addressBookMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
    }

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 下单数据
     * @return 订单确认数据
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 获取地址消息，判断地址簿是否为空，空则抛出异常
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        Long userId = BaseContext.getCurrentId();

        // 2. 获取购物车信息
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);
        if (shoppingCarts == null || shoppingCarts.isEmpty()) {
            // 购物车信息为空，抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 3. 获取菜品信息，进行库存校验，扣减库存
        shoppingCarts.forEach(cart -> {
            Dish dish = dishMapper.getById(cart.getDishId());
            if (dish.getStock() < cart.getNumber()) {
                // 菜品库存不足，抛出业务异常
                throw new DishBusinessException(MessageConstant.DISH_STOCK_INSUFFICIENT);
            }
            log.info("当前库存为：{}-{}",dish.getName(), dish.getStock());
            log.info("购物车项菜品数量为：{}-{}",cart.getName(), cart.getNumber());
            log.info("扣减后的库存为：{}", dish.getStock()-cart.getNumber());
            dish.setStock(dish.getStock() - cart.getNumber());
            dishMapper.update(dish);
        });

        // 4. 插入订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis())+ userId);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());//收货人
        orders.setAddress(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());//收获地址
        orders.setUserId(userId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetails = new ArrayList<>();
        // 5. 插入n条订单明细数据
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//获取订单id
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBath(orderDetails);

        // 6. 清空购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 7. 发送延迟消息 在用户下单后30分钟内未支付则取消该订单，并补偿库存
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
        }, 30000, 3/*16*/);

        // 8. 封装VO并返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
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
