package com.smart.service.impl;

import com.smart.constant.StatusConstant;
import com.smart.entity.Dish;
import com.smart.entity.DishFlavor;
import com.smart.mapper.DishFlavorMapper;
import com.smart.mapper.DishMapper;
import com.smart.service.DishService;
import com.smart.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DishServiceImpl implements DishService {

    private final DishMapper dishMapper;

    private final DishFlavorMapper dishFlavorMapper;

    public DishServiceImpl(DishMapper dishMapper, DishFlavorMapper dishFlavorMapper) {
        this.dishMapper = dishMapper;
        this.dishFlavorMapper = dishFlavorMapper;
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId 分类id
     * @return 菜品数据
     */
    @Override
    public List<DishVO> getDishListByCategoryId(Long categoryId) {

        // 1. 构建菜品查询实体
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        // 2. 执行查询
        List<Dish> dishList = dishMapper.list(dish);

        // 3. 创建DishVO对象列表，用于封装查询结果
        List<DishVO> dishVOList = new ArrayList<>();

        // 4. 遍历查询结果，将Dish数据封装为DishVO对象，并添加到列表中
        for (Dish d : dishList) {
            DishVO dishVO = new DishVO();
            BeanUtils.copyProperties(d, dishVO);

            // 4.1. 根据菜品id查询对应的口味
            List<DishFlavor> flavors = dishFlavorMapper.getByDishId(d.getId());

            dishVO.setFlavors(flavors);
            dishVOList.add(dishVO);
        }

        // 5. 返回DishVO对象列表
        return dishVOList;
    }
}
