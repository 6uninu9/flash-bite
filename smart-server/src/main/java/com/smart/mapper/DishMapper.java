package com.smart.mapper;

import com.smart.annotation.AutoFill;
import com.smart.dto.DishPageQueryDTO;
import com.smart.entity.Dish;
import com.smart.enumeration.OperationType;
import com.smart.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 动态条件查询菜品
     * @param dish 查询条件
     * @return 菜品数据列表
     */
    List<Dish> list(Dish dish);

    /**
     * 根据id查询菜品
     * @param dishId 菜品id
     * @return 菜品数据
     */
    @Select("select id, name, category_id, price, spike_price, image, description, status, create_time, update_time, create_user, update_user, stock, activity_stock, is_spike from dish where id = #{dishId}")
    Dish getById(Long dishId);

    /**
     * 更新菜品数据
     * @param dish 菜品数据
     */
    void update(Dish dish);

    /**
     * 扣减菜品库存
     *
     * @param dishId 菜品id
     * @param number 扣减数量
     * @return 影响行数
     */
    @Update("update dish set stock = stock - #{number} where id = #{dishId} and stock >= #{number}")
    int deductStockByDishId(Long dishId, Integer number);

    /**
     * 新增菜品数据
     * @param dish 菜品数据
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 批量删除菜品
     * @param ids 菜品id列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO 分页查询参数
     * @return 菜品分页结果
     */
    List<DishVO> queryPage(DishPageQueryDTO dishPageQueryDTO);
}
