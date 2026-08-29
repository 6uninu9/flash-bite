package com.smart.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
import com.smart.dto.OrderReminderDTO;
import com.smart.dto.OrdersPaymentDTO;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.entity.*;
import com.smart.exception.*;
import com.smart.mapper.*;
import com.smart.service.OrderService;
import com.smart.vo.OrderPaymentVO;
import com.smart.vo.OrderSubmitVO;
import com.smart.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

    private final UserCouponMapper userCouponMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, AddressBookMapper addressBookMapper, ShoppingCartMapper shoppingCartMapper, RocketMQTemplate rocketMQTemplate, DishMapper dishMapper, UserCouponMapper userCouponMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.addressBookMapper = addressBookMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
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
        // 一张订单最多使用一张用户优惠券。
        Long userCouponId = ordersSubmitDTO.getUserCouponId();

        // tablewareNumber/packAmount 未传时若不兜底，复制到订单实体的原始类型字段会抛异常
        if (ordersSubmitDTO.getTablewareNumber() == null) {
            ordersSubmitDTO.setTablewareNumber(0);
        }
        if (ordersSubmitDTO.getPackAmount() == null) {
            ordersSubmitDTO.setPackAmount(0);
        }

        // 2. 获取地址簿
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        // 判断地址簿是否为空
        if (addressBook == null) {
            // 空则抛出异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 3. 查询购物车
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
            if (dish == null || Objects.equals(dish.getStatus(), Dish.DISABLE)) {
                // 空或者停售则抛出异常 终止下单
                throw new DishBusinessException(MessageConstant.DISH_IS_NOT_AVAILABLE);
            }
        });

        // 4. 服务端按“菜品单价 × 数量 + 打包费”计算优惠前金额，不能信任客户端金额。
        BigDecimal dishAmount = shoppingCarts.stream()
                .map(cart -> cart.getAmount().multiply(BigDecimal.valueOf(cart.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // packAmount 的请求单位为分，订单金额字段统一使用元。
        BigDecimal packAmount = BigDecimal.valueOf(ordersSubmitDTO.getPackAmount() == null ? 0 : ordersSubmitDTO.getPackAmount(), 2);
        BigDecimal originalAmount = dishAmount.add(packAmount);
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (userCouponId != null) {
            UserCoupon userCoupon = userCouponMapper.getById(userCouponId);
            if (userCoupon == null) {
                throw new OrderBusinessException(MessageConstant.COUPON_NOT_EXIST);
            }
            if (!userId.equals(userCoupon.getUserId())) {
                throw new OrderBusinessException(MessageConstant.COUPON_NOT_BELONG_TO_CURRENT_USER);
            }
            if (Objects.equals(userCoupon.getStatus(), UserCoupon.STATUS_USED)) {
                throw new OrderBusinessException(MessageConstant.COUPON_ALREADY_USED);
            }
            if (Objects.equals(userCoupon.getStatus(), UserCoupon.STATUS_EXPIRE)
                    || !userCoupon.getExpireTime().isAfter(LocalDateTime.now())) {
                throw new OrderBusinessException(MessageConstant.COUPON_ALREADY_EXPIRED);
            }
            if (!Objects.equals(userCoupon.getStatus(), UserCoupon.STATUS_AVAILABLE)) {
                throw new OrderBusinessException(MessageConstant.COUPON_OCCUPIED_BY_OTHER_ORDER);
            }
            if (!Objects.equals(userCoupon.getCouponType(), UserCoupon.TYPE_FULL_REDUCTION)
                    && !Objects.equals(userCoupon.getCouponType(), UserCoupon.TYPE_DIRECT_DISCOUNT)) {
                throw new OrderBusinessException(MessageConstant.COUPON_NOT_EXIST);
            }
            BigDecimal thresholdAmount = userCoupon.getThresholdAmount();
            if (originalAmount.compareTo(thresholdAmount) < 0) {
                throw new OrderBusinessException(String.format(MessageConstant.COUPON_MIN_PRICE_NOT_MET, thresholdAmount));
            }
            discountAmount = userCoupon.getDiscountAmount();
        }
        BigDecimal netPayAmount = originalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        // 7. 插入订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setAmount(netPayAmount); // 支付金额
        orders.setOriginalAmount(originalAmount);
        orders.setDiscountAmount(discountAmount);
        orders.setUserCouponId(userCouponId);
        orders.setNumber(IdUtil.getSnowflakeNextIdStr()); // 使用hutool工具包中的雪花算法生成订单号
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());
        orders.setUserId(userId);
        orderMapper.insert(orders);

        // 订单已生成后再原子锁券，失败时事务会回滚订单和后续写入。
        if (userCouponId != null && userCouponMapper.tryReserve(userCouponId, userId, orders.getId()) != 1) {
            throw new OrderBusinessException(MessageConstant.COUPON_OCCUPIED_BY_OTHER_ORDER);
        }

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
        // 在下单时就扣减库存 主要是因为:
        // 用户选完一堆菜品，填完地址，跳到支付页，却发现菜品已售空，用户体验极差，极易流失。
        // 除此之外 这里的扣减库存只是预扣减(用户超时未支付后释放库存) 后厨拿到单子进行出餐是在用户支付后
        shoppingCarts.forEach(cart -> {
            // 扣减库存并获取扣减后影响的行数用于判断是否扣减成功
            // 使用数据库隐式行锁+条件判断，避免并发问题和超卖
            int rows = dishMapper.deductStockByDishId(cart.getDishId(), cart.getNumber());
            if (rows == 0) {
                throw new OrderBusinessException(cart.getName() + "-" + MessageConstant.DISH_SOLD_OUT);
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
                .orderAmount(netPayAmount)
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
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderAndReleaseStock(Long orderId) {
        // 1. 条件更新订单状态为已取消：仅待付款(1)订单可被取消，防止与支付并发导致已支付订单被取消。
        if (orderMapper.cancelByIdIfStatus(orderId, Orders.PENDING_PAYMENT, "系统自动取消", LocalDateTime.now()) != 1) {
            // 订单已支付或已取消，无需释放优惠券与库存；重复消息也不会重复处理（幂等）。
            log.info("订单取消条件不满足，跳过处理，orderId={}", orderId);
            return;
        }

        // 2. 仅释放本订单仍处于锁定状态的优惠券，已过期的券保持已过期状态。
        userCouponMapper.releaseReservation(orderId);

        // 3. 释放库存
        // 3.1. 查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
        // 3.2. 遍历明细，逐一释放菜品库存
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

    /**
     * 订单支付
     * 原本的完整的支付流程：
     * 1. 前端点击支付，调用 /order/payment，返回真实预支付交易单给前端
     * 2. 前端收到真实预支付交易单后，调用微信支付接口，生成微信支付二维码
     * 3. 前端付款成功后，微信服务器自动调用的回调接口 /paySuccess
     * 4. 在/paySuccess 中完成修改订单为 已支付+待接单、核销优惠券，WebSocket 推送商家，
     * 最后返回支付成功的信息给前端，显示支付成功
     * 但是由于真实的支付中返回的OrderPaymentVO需要微信商户号才能调用微信官方 SDK，弹出微信支付界面，让用户扫码 / 输密码付款
     * 而只有企业才能获取微信商户号，所以这里只能模拟微信支付成功，直接省略了回调过程，
     * 直接完成修改订单为 已支付+待接单、核销优惠券，WebSocket 推送商家操作
     *
     * @param ordersPaymentDTO 支付数据
     * @return 订单支付结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        if (ordersPaymentDTO == null) {
            throw new OrderBusinessException(MessageConstant.PAY_PARAM_ERROR);
        }

        Long userId = BaseContext.getCurrentId();
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        if (!StringUtils.hasText(orderNumber)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NUMBER_IS_NULL);
        }
        Integer payMethod = ordersPaymentDTO.getPayMethod();
        if (payMethod == null || !payMethod.equals(Orders.PAYMETHOD_WECHAT) && !payMethod.equals(Orders.PAYMETHOD_ALIPAY)) {
            throw new OrderBusinessException(MessageConstant.PAY_METHOD_ERROR);
        }
        Long merchantId = ordersPaymentDTO.getMerchantId();
        if (merchantId == null) {
            throw new OrderBusinessException(MessageConstant.MERCHANT_NO_IS_NULL);
        }

        // 获取订单判断订单是否已经支付，避免重复支付
        Orders existOrder = orderMapper.getByNumber(orderNumber);
        if (existOrder == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 校验订单归属，防止越权支付他人订单
        if (!Objects.equals(existOrder.getUserId(), userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_BELONG_TO_CURRENT_USER);
        }
        if (Objects.equals(existOrder.getPayStatus(), Orders.PAID)) {
            throw new OrderBusinessException(MessageConstant.REPEAT_PAYMENT);
        }

        // 调用微信支付接口，生成预支付交易单
        // 但是,由于没有企业来注册商户号无法实现微信支付，所以这里只模拟微信支付成功
//        User user = userMapper.getById(userId);
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "云穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
        // 构建返回给前端的预支付交易单，以模拟微信支付成功
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        // 修改订单状态为待接单和已支付（条件更新：仅待付款且未支付订单可被支付）
        // 防止已取消订单被“复活”为已支付，以及支付与取消并发时状态被覆盖。
        if (orderMapper.updateStatus(Orders.TO_BE_CONFIRMED, Orders.PAID, orderNumber) != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_CHANGED);
        }

        // 仅核销当前订单锁定的那一张用户优惠券，避免按模板券 ID 误核销。
        if (existOrder.getUserCouponId() != null
                && userCouponMapper.markUsed(existOrder.getUserCouponId(), existOrder.getId()) != 1) {
            throw new OrderBusinessException(MessageConstant.COUPON_OCCUPIED_BY_OTHER_ORDER);
        }

        // 对商家进行来单提醒
        // 整体流程如下：
        // 1. 商家端登录后，会将一个 merchantId （商家id，唯一标识）和 JWT 令牌（令牌过期时间一般来说至少一天）一起返回给前端
        //    不过，一般来说会将merchantId放进 JWT 令牌中，让前端解析出merchantId
        // 2. 前端会利用会将一个 merchantId 与服务端建立 WebSocket 连接，
        //    即WebSocketServer中的@OnOpen 方法被触发，将 session 以 merchantId 为 key 存入 SESSION_MAP，
        //    之后连接保持长连接，以随时可以接收后端推送的消息。
        // 3. 用户在支付时请求体携带 merchantId，服务端接收到 merchantId 后调用 sendToAllClient 方法对商家发起来单提醒
        // 4. 商家退出登录后，前端主动关闭 WebSocket 连接，而后端 @OnClose 会自动从 SESSION_MAP 中移除该 merchantId 的会话。
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);//1表示来单提醒 2表示催单
        map.put("orderId", Math.toIntExact(existOrder.getId()));
        map.put("content", "订单号:" + orderNumber);
        WebSocketServer.sendToUser(String.valueOf(ordersPaymentDTO.getMerchantId()), JSON.toJSONString(map));

        return vo;
    }

    /**
     * 用户取消订单
     *
     * @param id 订单id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void userCancelById(Long id) {
        //根据订单id查询订单
        Orders orderDB = orderMapper.getById(id);

        //校验订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //校验订单归属，防止越权取消他人订单
        if (!Objects.equals(orderDB.getUserId(), BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_BELONG_TO_CURRENT_USER);
        }

        //检验订单状态 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (Objects.equals(orderDB.getStatus(), Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_MERCHANT_ACCEPTED);
        }
        if (Objects.equals(orderDB.getStatus(), Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_DELIVERING);
        }
        if (Objects.equals(orderDB.getStatus(), Orders.COMPLETED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_COMPLETED);
        }
        if (Objects.equals(orderDB.getStatus(), Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_CANCELLED);
        }

        // 订单处于待接单(2)状态下取消，需要进行退款；由于未实现真实微信支付，退款仅将支付状态置为退款，暂不调用微信退款接口
        // 若后续接入微信商户号，可在取消前调用 weChatPayUtil.refund 申请真实退款。
        // 条件更新：仅当订单仍处于原状态时才取消，防止取消与支付/超时取消并发导致状态被覆盖。
        Integer expectedStatus = orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)
                ? Orders.TO_BE_CONFIRMED : Orders.PENDING_PAYMENT;
        if (orderMapper.cancelByIdIfStatus(orderDB.getId(), expectedStatus, "用户取消", LocalDateTime.now()) != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_CHANGED);
        }

        // 仅释放本订单仍处于锁定状态的优惠券，已过期的券保持已过期状态。
        userCouponMapper.releaseReservation(id);

        // 释放库存
        // 查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
        // 遍历明细，逐一释放菜品库存
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

    /**
     * 订单催单
     *
     * @param orderReminderDTO 订单催单请求数据
     */
    @Override
    public void reminder(OrderReminderDTO orderReminderDTO) {
        Long orderId = orderReminderDTO.getOrderId();
        Long merchantId = orderReminderDTO.getMerchantId();

        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(orderId);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);//1表示来单提醒 2表示催单
        map.put("orderId", Math.toIntExact(orderId));
        map.put("content", "订单号:" + ordersDB.getNumber());
        WebSocketServer.sendToUser(String.valueOf(merchantId), JSON.toJSONString(map));
    }
}
