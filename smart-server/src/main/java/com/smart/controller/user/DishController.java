package com.smart.controller.user;

import com.smart.result.Result;
import com.smart.service.DishService;
import com.smart.vo.DishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/list")
    @Operation(summary = "根据分类id查询菜品")
    public Result<List<DishVO>> list(@NotNull Long categoryId) {
        List<DishVO> dishVOList = dishService.getDishListByCategoryId(categoryId);
        return Result.success(dishVOList);
    }

    // 根据菜品id查询菜品详情 将菜品数据缓存进redis 并使用布隆过滤器存储对应的菜品id 避免缓存穿透
    // 根据菜品名称模糊查询 使用Elasticsearch或OpenSearch存储数据（搜索功能）
}
