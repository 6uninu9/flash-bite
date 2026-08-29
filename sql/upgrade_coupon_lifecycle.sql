-- 优惠券闭环第一阶段：为已有数据库补充订单金额快照和锁券状态字段。
-- 执行前请先备份数据库；本脚本仅用于已通过旧版 init.sql 初始化的环境。

ALTER TABLE user_coupon
    ADD COLUMN reserved_at DATETIME NULL COMMENT '订单锁券时间' AFTER order_id;

ALTER TABLE user_coupon
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '优惠券状态：0-可用 1-已使用 2-已过期 3-已锁定';

ALTER TABLE orders
    ADD COLUMN original_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠前金额' AFTER amount,
    ADD COLUMN discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额' AFTER original_amount,
    ADD COLUMN user_coupon_id BIGINT NULL COMMENT '本订单使用的用户优惠券ID' AFTER discount_amount;
