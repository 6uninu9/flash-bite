package com.smart.enumeration;

import lombok.Getter;

import java.util.List;

/**
 * 枚举类：布隆过滤器业务类型
 */
@Getter
public enum BloomFilterBizEnum {

    // 菜品分类
    CATEGORY(
            "bloom:category:id",
            "bloom:category:id:new",
            "bloom:filter:category:set",
            "categoryServiceImpl"
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
    private final String dataSetKey;    // 缓存set key
    private final String serviceName;

    @FunctionalInterface // 函数式接口
    public interface DataSupplier {
        List<String> get(Object service);
    }

    BloomFilterBizEnum(String oldKey, String newKey, String dataSetKey, String serviceName) {
        this.oldKey = oldKey;
        this.newKey = newKey;
        this.dataSetKey = dataSetKey;
        this.serviceName = serviceName;
    }
}