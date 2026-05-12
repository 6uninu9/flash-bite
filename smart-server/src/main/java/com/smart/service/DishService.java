package com.smart.service;

import com.smart.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 根据分类id查询菜品
     * @param categoryId 分类id
     * @return 菜品数据
     */
    List<DishVO> getDishListByCategoryId(Long categoryId);
}
