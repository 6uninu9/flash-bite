package com.smart.service;

import com.smart.data.HotRankEvent;
import com.smart.dto.HotRankConfigDTO;
import com.smart.entity.Dish;
import com.smart.entity.OrderDetail;
import com.smart.enumeration.HotRankPeriod;
import com.smart.vo.HotDishRankVO;

import java.util.List;
import java.util.Map;

/**
 * 分布式菜品热销榜服务
 */
public interface HotDishRankingService {

    /**
     * 记录菜品浏览事件
     */
    void recordView(Dish dish, Long userId);

    /**
     * 记录菜品加购事件
     */
    void recordCart(Dish dish);

    /**
     * 记录订单中的菜品事件
     */
    void recordOrder(Long orderId, List<OrderDetail> orderDetails);

    /**
     * 查询全店热销榜
     */
    List<HotDishRankVO> topShop(HotRankPeriod period, int limit);

    /**
     * 查询分类热销榜
     */
    List<HotDishRankVO> topCategory(Long categoryId, HotRankPeriod period, int limit);

    /**
     * 获取当前生效的运营权重
     */
    HotRankConfigDTO getConfig();

    /**
     * 更新运营权重
     */
    void updateConfig(HotRankConfigDTO configDTO);

    /**
     * 获取当前实例的热销榜运行指标
     */
    Map<String, Long> getMetrics();

    /**
     * 重放热销榜补偿事件
     */
    void replay(HotRankEvent event);
}
