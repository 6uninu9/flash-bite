package com.smart.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.smart.constant.MessageConstant;
import com.smart.context.BaseContext;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    public OrderServiceImpl(OrderMapper orderMapper, OrderDetailMapper orderDetailMapper, AddressBookMapper addressBookMapper, ShoppingCartMapper shoppingCartMapper, RocketMQTemplate rocketMQTemplate, DishMapper dishMapper, CouponMapper couponMapper, UserCouponMapper userCouponMapper) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.addressBookMapper = addressBookMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.dishMapper = dishMapper;
        this.couponMapper = couponMapper;
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
        // 获取用户优惠卷id集合
        List<Long> userCouponIds = ordersSubmitDTO.getUserCouponIds();

        // 1. 判断使用的优惠卷有没有给其他订单使用，即用户持有的优惠卷有没有订单id，没有则添加订单id
        //    并返回用户优惠卷集合用于主线程批量添加订单id
        // 主要是为了解决 用户下单使用了优惠卷，但是未支付，而另下单使用相同的优惠卷导致最后支付发生的优惠卷使用冲突的问题
        // 在用户下单时占用使用的优惠卷 除非用户主动取消订单、用户支付超时系统自动取消订单、商家拒单、执行发生异常将用户持有的优惠卷中的订单id置为空
        // 否则不能使用同一张优惠卷重复下单
        // 但是一般来说用户在选择需要使用的优惠卷时应该看不到有订单使用的优惠卷或者是灰色不可选择的，但是为了以防万一，还是应该进行检查
        List<UserCoupon> userCoupons;
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            // 返回空集合
            userCoupons = List.of();
        } else {
            // 获取用户使用的优惠卷
            userCoupons = userCouponMapper.getByIds(userCouponIds);
            if (CollectionUtils.isEmpty(userCoupons)) {
                throw new OrderBusinessException(MessageConstant.COUPON_NOT_EXIST);
            }
            // 遍历用户占用的优惠卷，进行相关校验
            userCoupons.forEach(userCoupon -> {
                // 校验归属用户
                if (!userId.equals(userCoupon.getUserId())) {
                    throw new OrderBusinessException(MessageConstant.COUPON_NOT_BELONG_TO_CURRENT_USER);
                }
                // 校验是否已使用
                if (userCoupon.getStatus() == UserCoupon.STATUS_USED) {
                    throw new OrderBusinessException(MessageConstant.COUPON_ALREADY_USED);
                }
                // 校验是否已过期
                if (userCoupon.getStatus() == UserCoupon.STATUS_EXPIRE) {
                    throw new OrderBusinessException(MessageConstant.COUPON_ALREADY_EXPIRED);
                }
                if (userCoupon.getOrderId() != null) {
                    // 优惠卷已被其他订单占用
                    throw new BaseException(MessageConstant.COUPON_OCCUPIED_BY_OTHER_ORDER);
                }
            });
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

        // 4. 查询优惠卷获取优惠后的支付金额
        // 能够选择的优惠卷应该由前端筛选，所以这里只需计算前端传来的优惠卷id对应的优惠金额总和并返回即可
        // 事实上，为了拓展性应该在user_coupon表中添加扣减金额discount_amount、优惠卷名称name、有效时间valid_days这三个字段
        // 因为运营可能会修改优惠卷的金额、名称、有效时间，但是用户领取过的优惠卷是不应该被修改的，不然容易引发投诉
        // 其次计算扣减金额时都要查询优惠卷，高并发下性能有所影响
        // 所以除非规定优惠卷永不修改，才可以使用如下代码，但是如果业务多变、运营强势，那么最好添加
        // 而我没有修改是觉得麻烦 因为一旦修改 又要修改其他功能模块 比如秒杀优惠卷 所以就暂时搁置
        BigDecimal netPayAmount;
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            netPayAmount = BigDecimal.ZERO;
        } else {
            // 获取优惠卷id集合，复用用户优惠卷查询结果
            List<Long> couponIds = userCoupons.stream().map(UserCoupon::getCouponId).toList();

            // 根据优惠卷id查询对应的优惠卷
            List<Coupon> coupons = couponMapper.selectBatchById(couponIds);

            // 获取用户支付的原始金额，复用购物车查询结果
            BigDecimal originalAmount = shoppingCarts.stream().map(ShoppingCart::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 判断优惠卷是否满足使用条件
            for (Coupon coupon : coupons) {
                if (coupon == null) continue;
                BigDecimal minPrice = coupon.getThresholdAmount(); // 优惠券满减门槛：满XX可用
                if (originalAmount.compareTo(minPrice) < 0) {
                    // 构建异常信息
                    String message = String.format(MessageConstant.COUPON_MIN_PRICE_NOT_MET, minPrice);
                    throw new OrderBusinessException(message);
                }
            }

            // 获取优惠卷集合中的优惠金额
            BigDecimal couponAmount = coupons.stream() // 把优惠券列表转换成 Stream 流
                    .filter(Objects::nonNull) // 过滤出不为空的对象
                    .map(Coupon::getDiscountAmount) // 类型转换，把流里的每一个优惠券对象，转换成对应的优惠金额
                    .reduce(BigDecimal.ZERO, BigDecimal::add) // 聚合计算，把流里的多个数据合并成一个数据，即将BigDecimal.ZERO与其他优惠金额进行相加
                    .setScale(2, RoundingMode.HALF_UP);// 将计算出的总金额保留 2 位小数 + 四舍五入

            // 计算优惠后的支付金额
            netPayAmount = originalAmount.subtract(couponAmount);
            // 如果优惠后金额 ≤ 0
            if (netPayAmount.compareTo(BigDecimal.ZERO) <= 0) {
                // 优惠金额不能超过订单金额，支付金额最低为0，将支付金额设置为0
                netPayAmount = BigDecimal.ZERO;
            }
        }

        // 7. 插入订单数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setAmount(netPayAmount); // 支付金额
        orders.setNumber(IdUtil.getSnowflakeNextIdStr()); // 使用hutool工具包中的雪花算法生成订单号
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

        // 9. 将用户持有的优惠卷添加对应的订单id，标记为已占用
        // ，但状态不改为已使用（支付后核销优惠卷时才改为已使用）
        if (!userCoupons.isEmpty()) {
            // 如果集合不为空，则批量更新
            userCouponMapper.updateOrderIdBathByIds(orders.getId(), userCoupons);
        }

        // 10. 扣减库存
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

        // 11. 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 12. 发送延迟消息
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

        // 13. 返回结果
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
    public void cancelOrderAndReleaseStock(String orderId) {
        // 1. 取消订单
        Orders orders = Orders.builder()
                .id(Long.valueOf(orderId))
                .status(Orders.CANCELLED)
                .cancelReason("系统自动取消")
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        // 2. 移除对应订单占用的用户优惠卷的订单id，让对应的用户优惠卷不再被占用
        userCouponMapper.removeOrderIdByOrderId(Long.valueOf(orderId));

        // 3. 释放库存
        // 3.1. 查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(Long.valueOf(orderId));
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

        // 修改订单状态为待接单和已支付
        orderMapper.updateStatus(Orders.TO_BE_CONFIRMED, Orders.PAID, orderNumber);

        // 将订单中使用的优惠卷改为已使用
        // 根据订单id和用户id查询订单使用的优惠卷
        List<UserCoupon> couponUser = userCouponMapper.getByOrderIdAndUserId(existOrder.getId(), userId);
        // 如果有使用优惠卷则将优惠卷改为已使用
        if (couponUser != null && !couponUser.isEmpty()) {
            // 获取优惠卷id集合
            List<Long> couponUserIds = couponUser.stream().map(UserCoupon::getCouponId).toList();
            // 批量修改优惠卷状态为已使用
            userCouponMapper.updateBathStatus(couponUserIds, userId, UserCoupon.STATUS_USED);
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
}
