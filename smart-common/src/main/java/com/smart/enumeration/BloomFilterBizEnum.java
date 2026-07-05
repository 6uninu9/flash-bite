package com.smart.enumeration;

import lombok.Getter;

/**
 * 枚举类：布隆过滤器业务类型
 */
@Getter
public enum BloomFilterBizEnum {

    // 菜品分类
    CATEGORY(
            "bloom:category:id",
            "bloom:category:id:new",
            "categoryServiceImpl"
    ),

    // 优惠卷
    COUPON(
            "bloom:coupon:id",
            "bloom:coupon:id:new",
            "couponServiceImpl"
    );

    // 菜品
//    DISH(
//            "bloom:dish:id",
//            "bloom:dish:id:new",
//            "bloom:filter:dish:set",
//            "dishService"
//    );

    //其他业务数据....

    private final String oldKey;       // 旧布隆key
    private final String newKey;       // 临时key
    private final String serviceName;

    BloomFilterBizEnum(String oldKey, String newKey, String serviceName) {
        this.oldKey = oldKey;
        this.newKey = newKey;
        this.serviceName = serviceName;
    }
}