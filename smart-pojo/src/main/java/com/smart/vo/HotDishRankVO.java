package com.smart.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 菜品热销榜展示数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotDishRankVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer rank;
    private Double score;
    private Long dishId;
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private String image;
    private String description;
}
