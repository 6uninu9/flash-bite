package com.smart.controller.user;

import com.smart.result.Result;
import com.smart.service.DishService;
import com.smart.enumeration.HotRankPeriod;
import com.smart.vo.DishVO;
import com.smart.vo.HotDishRankVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Tag(name = "C端-菜品浏览接口")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId 分类id
     * @return 菜品列表
     */
    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
    public Result<List<DishVO>> list(@NotNull Long categoryId) {
        List<DishVO> dishVOList = dishService.getDishListByCategoryId(categoryId);
        return Result.success(dishVOList);
    }

    /**
     * 查询菜品详情
     * @param id 菜品id
     * @return 菜品详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询菜品详情")
    public Result<DishVO> getDetail(@PathVariable Long id) {
        return Result.success(dishService.getUserDishDetail(id));
    }

    /**
     * 查询全店热销榜
      * @param period 热销榜时间周期，枚举
      * @param limit 热销榜菜品数量，top-limit
      * @return 热销榜菜品列表
     */
    @GetMapping("/hot")
    @Operation(summary = "查询全店热销榜")
    public Result<List<HotDishRankVO>> getShopHotRank(
            @RequestParam(defaultValue = "HOUR") HotRankPeriod period,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(dishService.getShopHotRank(period, limit));
    }

    /**
     * 查询分类热销榜
     * @param categoryId 分类id
     * @param period 热销榜时间周期
     * @param limit 热销榜菜品数量
     * @return 热销榜菜品列表
     */
    @GetMapping("/hot/category/{categoryId}")
    @Operation(summary = "查询分类热销榜")
    public Result<List<HotDishRankVO>> getCategoryHotRank(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "HOUR") HotRankPeriod period,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(dishService.getCategoryHotRank(categoryId, period, limit));
    }
}
