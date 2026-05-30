package com.smart.service;

import com.smart.dto.DishDTO;
import com.smart.dto.DishPageQueryDTO;
import com.smart.result.PageResult;
import com.smart.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 根据分类id查询菜品
     * @param categoryId 分类id
     * @return 菜品数据
     */
    List<DishVO> getDishListByCategoryId(Long categoryId);

    /**
     * 保存菜品数据
     *
     * @param dishDTO 菜品数据
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 修改菜品数据
     *
     * @param dishDTO 菜品数据
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 批量删除菜品
     *
     * @param ids 菜品id列表
     */
    void deleteBatch(List<Long> ids);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO 菜品分页查询参数
     * @return 菜品分页结果
     */
    PageResult<DishVO> queryPage(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id 菜品id
     * @return 菜品数据
     */
    DishVO getByIdWithFlavor(Long id);
}
