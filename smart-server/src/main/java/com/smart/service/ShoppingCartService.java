package com.smart.service;


import com.smart.dto.ShoppingCartDTO;
import com.smart.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    /**
     * 添加购物车
     * @param shoppingCartDTO 购物车数据DTO
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车
     * @return 购物车列表
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 清空购物车
     */
    void cleanShoppingCart();

    /**
     * 购物车商品数量-1
     * @param shoppingCartDTO 购物车数据DTO
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);
}
