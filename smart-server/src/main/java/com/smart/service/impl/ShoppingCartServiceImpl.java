package com.smart.service.impl;

import com.smart.context.BaseContext;
import com.smart.dto.ShoppingCartDTO;
import com.smart.entity.Dish;
import com.smart.entity.ShoppingCart;
import com.smart.mapper.DishMapper;
import com.smart.mapper.ShoppingCartMapper;
import com.smart.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ShoppingCartMapper shoppingCartMapper;

    private final DishMapper dishMapper;

    public ShoppingCartServiceImpl(ShoppingCartMapper shoppingCartMapper, DishMapper dishMapper) {
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
    }

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO 购物车数据DTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 判断购物车表中是否存在相应的菜品或套餐
        // 1.先将DTO数据迁移（复制）至（给）对应的实体（ShoppingCart）
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        // 2.获取用户id
        shoppingCart.setUserId(BaseContext.getCurrentId());
        // 3.获得上述查询条件后，查询出购物车中对应的菜品，以供判断菜品是否存在
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);//使用集合来返回数据以保持接口的通用性

        // 4. 如果存在，商品数量直接加一
        // 不用校验库存，在下单时才进行库存扣减，才需要进行库存校验
        // 而用户也完全能接受「下单时提示没货」，这是所有人都习惯的体验
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.getFirst();//获取购物车中的第一个元素，这第一个元素就是查询出来的菜品
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cart);
        } else {
            // 5. 如果不存在，添加到购物车，数量默认为1
            Long dishId = shoppingCartDTO.getDishId();

            Dish dish = dishMapper.getById(dishId);

            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 查看购物车
     *
     * @return 购物车数据列表
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        // 1. 获取用户id
        Long userId = BaseContext.getCurrentId();
        // 2. 利用构建器将查询条件填入shoppingcart 中
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 购物车商品数量-1
     * @param shoppingCartDTO 购物车数据DTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 1.先将DTO数据迁移（复制）至（给）对应的实体（ShoppingCart）
        ShoppingCart s = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, s);
        // 2. 根据菜品id和菜品口味查询购物车项 因为可能同一个菜品有不同口味或者没有口味
        ShoppingCart shoppingCart = shoppingCartMapper.getByDishIdAndDishFlavor(s);

        // 3. 判断购物车项数量
        if (shoppingCart.getNumber() > 1) {
            // 3.1. 如果购物车项数量大于1，数量减一
            shoppingCart.setNumber(shoppingCart.getNumber() - 1);
            shoppingCartMapper.updateNumberById(shoppingCart);
        } else {
            // 3.2. 如果购物车项数量等于1，则直接删除该购物车项
            shoppingCartMapper.deleteById(shoppingCart.getId());
        }
    }

}
