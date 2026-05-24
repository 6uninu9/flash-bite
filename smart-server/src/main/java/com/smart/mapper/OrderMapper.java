package com.smart.mapper;

import com.smart.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders 订单数据
     */
    void insert(Orders orders);

    /**
     * 根据id查询订单数据
     * @param orderId 订单id
     * @return 订单数据
     */
    @Select("select id, number, status, user_id, address_book_id," +
            " order_time, checkout_time, pay_method, pay_status," +
            " amount, remark, phone, address, user_name, consignee," +
            " cancel_reason, rejection_reason, cancel_time," +
            " estimated_delivery_time, delivery_status," +
            " delivery_time, pack_amount, tableware_number, tableware_status" +
            " from orders where id = #{orderId}")
    Orders getById(Long orderId);

    /**
     * 更新订单数据
     * @param orders 订单数据
     */
    void update(Orders orders);

    /**
     * 根据订单号修改订单状态
     *
     * @param status      订单状态
     * @param payStatus   支付状态
     * @param orderNumber 订单号
     */
    @Update("update orders set status = #{status}, pay_status = #{payStatus} where number = #{orderNumber}")
    void updateStatus(Integer status, Integer payStatus, String orderNumber);

    /**
     * 根据订单号查询订单id
     * @param orderNumber 订单号
     * @return 订单id
     */
    @Select("select id from orders where number = #{orderNumber}")
    Long getIdByNumber(String orderNumber);

    /**
     * 根据订单号查询订单数据
     * @param orderNumber 订单号
     * @return 订单数据
     */
    @Select("select id, number, status, user_id, address_book_id," +
            " order_time, checkout_time, pay_method, pay_status," +
            " amount, remark, phone, address, user_name, consignee," +
            " cancel_reason, rejection_reason, cancel_time," +
            " estimated_delivery_time, delivery_status," +
            " delivery_time, pack_amount, tableware_number, tableware_status" +
            " from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);
}
