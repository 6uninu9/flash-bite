package com.smart.constant;

/**
 * 用于存储 JWT 声明（claims）相关常量的类。
 * 这些常量可作为 JWT 中特定信息的键，方便在处理 JWT 时使用，避免硬编码字符串。
 */
public class JwtClaimsConstant {
    /**
     * JWT 中表示员工 ID 的声明键。
     */
    public static final String EMP_ID = "empId";
    /**
     * JWT 中表示用户 ID 的声明键。
     */
    public static final String USER_ID = "userId";
    /**
     * JWT 中表示商户 ID 的声明键。
     */
    public static final String MERCHANT_ID = "merchantId";
    /**
     * JWT 中表示角色的声明键。
     */
    public static final String ROLE = "role";
}
