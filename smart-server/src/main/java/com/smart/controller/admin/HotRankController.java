package com.smart.controller.admin;

import com.smart.dto.HotRankConfigDTO;
import com.smart.result.Result;
import com.smart.service.HotDishRankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商家端热销榜运营接口
 */
@RestController
@RequestMapping("/admin/hot-rank")
@Tag(name = "商家端-热销榜运营接口")
public class HotRankController {

    private final HotDishRankingService hotDishRankingService;

    public HotRankController(HotDishRankingService hotDishRankingService) {
        this.hotDishRankingService = hotDishRankingService;
    }

    /**
     * 查询热销榜事件权重
     * @return 热销榜事件权重
     */
    @GetMapping("/config")
    @Operation(summary = "查询热销榜事件权重")
    public Result<HotRankConfigDTO> getConfig() {
        return Result.success(hotDishRankingService.getConfig());
    }

    /**
     * 更新热销榜事件权重
     * @param configDTO 热销榜事件权重
     */
    @PutMapping("/config")
    @Operation(summary = "更新热销榜事件权重")
    public Result<String> updateConfig(@Valid @RequestBody HotRankConfigDTO configDTO) {
        hotDishRankingService.updateConfig(configDTO);
        return Result.success();
    }

    /**
     * 查询当前实例热销榜运行指标
     * @return 热销榜运行指标
     */
    @GetMapping("/metrics")
    @Operation(summary = "查询当前实例热销榜运行指标")
    public Result<Map<String, Long>> getMetrics() {
        return Result.success(hotDishRankingService.getMetrics());
    }
}
