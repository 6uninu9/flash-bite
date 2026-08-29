package com.smart.mapper;

import com.smart.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

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
            " amount, original_amount, discount_amount, user_coupon_id," +
            " remark, phone, address, user_name, consignee," +
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
     * 根据订单号修改订单状态（条件更新）
     * 仅当订单仍处于待付款(1)且未支付(0)时才允许更新，防止已取消/已支付订单被误置为已支付。
     *
     * @param status      订单状态
     * @param payStatus   支付状态
     * @param orderNumber 订单号
     * @return 受影响行数，1 表示更新成功
     */
    @Update("update orders set status = #{status}, pay_status = #{payStatus} " +
            "where number = #{orderNumber} and status = 1 and pay_status = 0")
    int updateStatus(Integer status, Integer payStatus, String orderNumber);

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
            " amount, original_amount, discount_amount, user_coupon_id," +
            " remark, phone, address, user_name, consignee," +
            " cancel_reason, rejection_reason, cancel_time," +
            " estimated_delivery_time, delivery_status," +
            " delivery_time, pack_amount, tableware_number, tableware_status" +
            " from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 条件取消订单（条件更新）
     * 仅当订单仍处于指定状态时才置为已取消，防止取消与支付/超时取消并发导致状态被覆盖。
     * 待接单(2)订单取消时顺带将支付状态置为退款(2)；待付款(1)订单取消时支付状态保持不变。
     * 注意：不能以 SET 内的 status 字段做 CASE 判断，MySQL 赋值表达式会从左到右读到已更新的值。
     *
     * @param id             订单id
     * @param expectedStatus 期望的订单当前状态（1-待付款 2-待接单）
     * @param cancelReason   取消原因
     * @param cancelTime     取消时间
     * @return 受影响行数，1 表示取消成功
     */
    @Update("update orders set status = 6, cancel_reason = #{cancelReason}, cancel_time = #{cancelTime}, " +
            "pay_status = IF(#{expectedStatus} = 2, 2, pay_status) " +
            "where id = #{id} and status = #{expectedStatus}")
    int cancelByIdIfStatus(Long id, Integer expectedStatus, String cancelReason, LocalDateTime cancelTime);
}
