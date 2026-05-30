package com.smart.controller.admin;

import com.smart.dto.DishDTO;
import com.smart.dto.DishPageQueryDTO;
import com.smart.result.PageResult;
import com.smart.result.Result;
import com.smart.service.DishService;
import com.smart.vo.DishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/dish")
@Tag(name = "菜品管理接口")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    /**
     * 新增菜品
     *
     * @param dishDTO 菜品数据
     * @return 响应结果
     */
    @PostMapping
    @Operation(
            summary = "新增菜品"
    )
    public Result<String> save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);

        dishService.saveWithFlavor(dishDTO);

        return Result.success();
    }

    /**
     * 修改菜品
     *
     * @param dishDTO 菜品数据
     * @return 响应结果
     */
    @PutMapping
    @Operation(
            summary = "修改菜品"
    )
    public Result<String> update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);

        dishService.updateWithFlavor(dishDTO);

        return Result.success();
    }

    /**
     * 批量删除菜品
     *
     * @param ids 菜品id列表
     * @return 响应结果
     */
    @DeleteMapping
    @Operation(
            summary = "批量删除菜品"
    )
    public Result<T> delete(@RequestParam List<Long> ids) {
        log.info("批量删除菜品：{}", ids);

        dishService.deleteBatch(ids);

        return Result.success();
    }

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO 菜品分页查询参数
     * @return 菜品分页查询结果
     */
    @GetMapping("/page")
    @Operation(
            summary = "菜品分页查询"
    )
    public Result<PageResult<DishVO>> page(DishPageQueryDTO dishPageQueryDTO) {// 使用了URL查询参数/表单参数，所以没有使用@RequestBody
        log.info("分页查询参数：{}", dishPageQueryDTO);

        PageResult<DishVO> pageResult = dishService.queryPage(dishPageQueryDTO);

        return Result.success(pageResult);
    }

    /**
     * 根据id查询菜品
     *
     * @param id 菜品id
     * @return 菜品数据
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "根据id查询菜品"
    )
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("查询菜品id：{}", id);

        DishVO dishVO = dishService.getByIdWithFlavor(id);

        return Result.success(dishVO);
    }
}
