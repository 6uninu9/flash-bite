package com.smart.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 菜品热销榜事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotRankEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String eventId; // 事件ID
    private Long dishId; // 菜品ID
    private Long categoryId; // 类别ID
    private Double score; // 分数
    private Long eventTime; // 事件发生时间

    /**
     * 事件幂等键的存活秒数
     */
    private Long dedupSeconds;
}
