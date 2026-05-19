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
public class Coupon implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 优惠券名称
     */
    private String couponName;

    /**
     * 优惠券类型 1满减 2直减
     */
    private Integer couponType;

    /**
     * 满减门槛 0=无门槛
     */
    private BigDecimal thresholdAmount;

    /**
     * 优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 总发放数量
     */
    private Integer totalStock;

    /**
     * 剩余库存
     */
    private Integer surplusStock;

    /**
     * 领取开始时间
     */
    private LocalDateTime startTime;

    /**
     * 领取结束时间
     */
    private LocalDateTime endTime;

    /**
     * 领取后有效天数
     */
    private Integer validDays;

    /**
     * 状态 0未开始 1进行中 2已结束
     */
    private Integer status;

    /**
     * 创建人ID
     */
    private Long createUser;

    /**
     * 修改人ID
     */
    private Long updateUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否秒杀优惠券 0=普通发放券 1=秒杀抢购券
     */
    private Integer isSeckill;

    /*
      优惠券类型
     */
    /**
     * 1 满减券
     */
    public static final int TYPE_FULL_REDUCE = 1;
    /**
     * 2 直减券
     */
    public static final int TYPE_DIRECT_REDUCE = 2;

    /*
      优惠券状态
     */
    /**
     * 0 未开始
     */
    public static final int STATUS_NOT_START = 0;
    /**
     * 1 进行中
     */
    public static final int STATUS_RUNNING = 1;
    /**
     * 2 已结束
     */
    public static final int STATUS_END = 2;


    /*
      优惠券是否是秒杀优惠券
     */
    /**
     * 0 普通发放优惠券
     */
    public static final int IS_SECKILL_NO = 0;
    /**
     * 1 秒杀抢购优惠券
     */
    public static final int IS_SECKILL_YES = 1;
}