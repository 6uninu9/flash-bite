package com.smart.mapper;

import com.smart.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetails 订单明细集合
     */
    void insertBath(List<OrderDetail> orderDetails);

    @Select("select id, name, image, order_id, dish_id, dish_flavor, number, amount from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);
}
