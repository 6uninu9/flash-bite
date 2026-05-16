package com.smart.mapper;

import com.smart.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 查询购物车数据
     * @param shoppingCart 查询条件
     * @return 购物车数据列表
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 更新购物车中的数量
     * @param cart 购物车数据
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart cart);

    /**
     * 插入购物车数据
     * @param shoppingCart 购物车数据
     */
    @Insert("insert into shopping_cart(name, image, user_id, dish_id, dish_flavor,number, amount, create_time) " +
            "VALUES (#{name},#{image},#{userId},#{dishId},#{dishFlavor},#{number},#{amount},#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户id删除购物车数据
     * @param userId 用户id
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 根据购物车id查询购物车数据
     * @param shoppingCartId 购物车id
     * @return 购物车数据
     */
    @Select("select * from shopping_cart where id = #{shoppingCartId}")
    ShoppingCart getById(Long shoppingCartId);

    /**
     * 根据购物车id删除购物车数据
     * @param shoppingCartId 购物车id
     */
    @Delete("delete from shopping_cart where id = #{shoppingCartId}")
    void deleteById(Long shoppingCartId);

    /**
     * 根据菜品id和菜品口味查询购物车数据
     * @param s 查询条件
     * @return 购物车数据
     */
    ShoppingCart getByDishIdAndDishFlavor(ShoppingCart s);
}
