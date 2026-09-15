package com.smart.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 菜品热销榜配置（smart.hot-rank.*，来源 application.yml）
 *
 * 说明：这里的权重只是"启动时的默认兜底值"。运行期若运营通过 PUT /admin/hot-rank/config
 * 把权重写入了 Redis Hash（rank:dish:config:weights），各实例会以 Redis 中的值为准，
 * 最多在 configCacheSeconds(=10) 秒后生效。
 */
@Data
@Component
@ConfigurationProperties(prefix = "smart.hot-rank")
public class HotRankProperties {

    /**
     * 浏览事件默认权重（每次浏览菜品 +1）
     */
    private double viewWeight = 1D;

    /**
     * 加购事件默认权重（每次加入购物车 +3）
     */
    private double cartWeight = 3D;

    /**
     * 下单事件默认权重（每张订单中每个菜品 +10）
     */
    private double orderWeight = 10D;

    /**
     * 小时榜单个时间片长度，单位：秒。默认 300 = 5 分钟一个分片，
     * 写入时按"事件时间/片长"把事件归入对应的分片 Key（rank:dish:...:hour:{slice}）
     */
    private int sliceSeconds = 300;

    /**
     * 小时榜包含的时间片数量。默认 12：12 × 5 分钟 = 1 小时，
     * 查询小时榜时并集"当前分片及往前共 windowSlices 个分片"
     */
    private int windowSlices = 12;

    /**
     * 同一用户浏览同一菜品的去重时间，单位：秒。默认 300。
     * 实现为事件幂等键 view:{userId}:{dishId} 的 TTL，TTL 内重复浏览不计分；
     * 由于键到期即失效，构成"任意连续 5 分钟最多一次"的滚动窗口（与 sliceSeconds 相互独立）
     */
    private int viewDedupSeconds = 300;

    /**
     * 单次榜单查询最大返回数量（Top-N 上限），防止调用方一次取过多数据
     */
    private int maxLimit = 50;

    /**
     * 运营权重配置的本地缓存时间，单位：秒。默认 10。
     * 每个实例把"从 Redis 读到的权重"缓存 10 秒，避免每个事件都访问 Redis；
     * 超过该时间后下一次记分/查榜前会重新拉取（见 HotDishRankingServiceImpl.refreshConfigIfNecessary）
     */
    private int configCacheSeconds = 10;
}
