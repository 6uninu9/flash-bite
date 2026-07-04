package com.smart.service.impl;

import com.smart.context.BaseContext;
import com.smart.entity.UserCoupon;
import com.smart.mapper.UserCouponMapper;
import com.smart.service.UserCouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class UserCouponServiceImpl implements UserCouponService {

    private final UserCouponMapper userCouponMapper;

    public UserCouponServiceImpl(UserCouponMapper userCouponMapper) {
        this.userCouponMapper = userCouponMapper;
    }

    /**
     * 获取当前用户最近三个月领取的优惠券列表
     *
     * @return 用户优惠券列表
     */
    @Override
    public List<UserCoupon> list() {
        // 1. 获取当前登录用户ID（根据你项目的上下文工具调整，例如从Token解析）
        // 若你的方法入参已传入 userId，直接使用即可
        Long userId = BaseContext.getCurrentId();

        // 2. 计算三个月前的起始时间点
        // 例：2026-07-04 调用 → 起始时间为 2026-04-04 同一时刻
        LocalDateTime startTime = LocalDateTime.now().minusMonths(3);

        // 3. 查询并返回结果
        return userCouponMapper.listByUserAndTime(userId, startTime);
    }
}
