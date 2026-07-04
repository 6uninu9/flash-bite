package com.smart.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 优惠券id
     */
    private Long couponId;

    /**
     * 优惠券名称（冗余快照）
     */
    private String couponName;

    /**
     * 优惠券类型 1-满减 2-直减
     */
    private Integer couponType;

    /**
     * 满减门槛（0=无门槛）
     */
    private BigDecimal thresholdAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 领取时间
     */
    private LocalDateTime getTime;

    /**
     * 优惠券过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 是否秒杀优惠券 0=普通发放券 1=秒杀抢购券
     */
    private Integer isSeckill;

    /**
     * 使用时间
     */
    private LocalDateTime useTime;

    /**
     * 关联订单id
     */
    private Long orderId;

    /**
     * 用户持有优惠券状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 0 未使用
     */
    public static final int STATUS_UNUSED = 0;
    /**
     * 1 已使用
     */
    public static final int STATUS_USED = 1;
    /**
     * 2 已过期
     */
    public static final int STATUS_EXPIRE = 2;

    /**
     * 优惠券类型：满减
     */
    public static final int TYPE_FULL_REDUCTION = 1;
    /**
     * 优惠券类型：直减
     */
    public static final int TYPE_DIRECT_DISCOUNT = 2;

    /**
     * 普通发放券
     */
    public static final int SECKILL_NO = 0;
    /**
     * 秒杀抢购券
     */
    public static final int SECKILL_YES = 1;
}