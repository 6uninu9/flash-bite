package com.smart.service;

import com.smart.dto.OrdersPaymentDTO;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.entity.Orders;
import com.smart.vo.OrderPaymentVO;
import com.smart.vo.OrderSubmitVO;

public interface OrderService {
    /**
     * 用户下单
     * @param ordersSubmitDTO 下单数据
     * @return 订单确认数据
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 获取订单信息
     * @param orderId 订单id
     * @return 订单信息
     */
    Orders getOrderById(Long orderId);

    /**
     * 取消订单并释放库存
     * @param orderId 订单id
     */
    void cancelOrderAndReleaseStock(Long orderId);

    /**
     * 订单支付
     * @param ordersPaymentDTO 订单支付数据
     * @return 订单支付结果
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);
}
