package com.smart.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 热销榜运营权重配置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotRankConfigDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "浏览权重不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "浏览权重必须大于0")
    private Double viewWeight;

    @NotNull(message = "加购权重不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "加购权重必须大于0")
    private Double cartWeight;

    @NotNull(message = "下单权重不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "下单权重必须大于0")
    private Double orderWeight;
}
