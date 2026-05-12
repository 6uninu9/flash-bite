package com.smart.constant;

/**
 * 公共字段自动填充相关常量类，用于集中管理与实体类字段自动填充相关的方法名称常量。
 * 在项目中，可能会通过反射调用这些方法来自动设置实体类的创建时间、更新时间、创建用户和更新用户等字段。
 */
public class AutoFillConstant {
    /**
     * 实体类中设置创建时间的方法名称。
     * 例如在使用 MyBatis Plus 自动填充功能时，可能会通过反射调用此方法来设置实体类的创建时间字段。
     */
    public static final String SET_CREATE_TIME = "setCreateTime";

    /**
     * 实体类中设置更新时间的方法名称。
     * 用于在业务逻辑中自动更新实体类的更新时间字段。
     */
    public static final String SET_UPDATE_TIME = "setUpdateTime";

    /**
     * 实体类中设置创建用户的方法名称。
     * 当创建实体时，可能会调用此方法来设置创建用户字段。
     */
    public static final String SET_CREATE_USER = "setCreateUser";

    /**
     * 实体类中设置更新用户的方法名称。
     * 在更新实体时，通过此方法设置更新用户字段。
     */
    public static final String SET_UPDATE_USER = "setUpdateUser";
}
