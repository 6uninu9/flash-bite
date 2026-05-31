package com.smart.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrdersSubmitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //地址簿id
    @NotNull(message = "地址簿ID不能为空")
    private Long addressBookId;
    //付款方式
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式错误")
    @Max(value = 2, message = "支付方式错误")
    private Integer payMethod;
    //备注
    private String remark;
    //预计送达时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime estimatedDeliveryTime;
    //配送状态  1立即送出  0选择具体时间
    @NotNull(message = "配送状态不能为空")
    @Min(value = 0, message = "配送状态错误")
    @Max(value = 1, message = "配送状态错误")
    private Integer deliveryStatus;
    //餐具数量
    @PositiveOrZero(message = "餐具数量不能为负数")
    private Integer tablewareNumber;
    //餐具数量状态  1按餐量提供  0选择具体数量
    @Min(value = 0, message = "餐具状态错误")
    @Max(value = 1, message = "餐具状态错误")
    private Integer tablewareStatus;
    //打包费
    @PositiveOrZero(message = "打包费不能为负数")
    private Integer packAmount;
    //总金额
    //注意：这里的amount我们后端会重新计算，所以只做基础校验
    @DecimalMin(value = "0.0", message = "订单金额不能小于0.0元")
    private BigDecimal amount;
    //用户优惠卷id集合（用户可能不止选了一张优惠卷）
    //为什么不使用优惠卷id：
    //1.无法支持使用多张同模板的优惠券
    //2.历史数据扩展困难，今天你规定每人一张，明天产品说“每人可领5张”，前端和后端都要大改
    //3.违反RESTful设计原则,资源是“用户拥有的优惠券”，应该用它的唯一标识（用户优惠券ID）来操作，而不是用“券模板ID”间接定位。
    private List<Long> userCouponIds;
}
