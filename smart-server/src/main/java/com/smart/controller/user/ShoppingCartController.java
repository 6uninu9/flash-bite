package com.smart.controller.user;

import com.smart.dto.ShoppingCartDTO;
import com.smart.entity.ShoppingCart;
import com.smart.result.Result;
import com.smart.service.ShoppingCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/user/shoppingCart")
@Tag(name = "C端购物车接口")
public class ShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    public ShoppingCartController(ShoppingCartService shoppingCartService) {
        this.shoppingCartService = shoppingCartService;
    }

    /**
     * 添加购物车（包括购物车数量+1）
     *
     * @param shoppingCartDTO 购物车数据DTO
     * @return 添加操作结果
     */
    @PostMapping("/add")
    @Operation(
            summary = "添加购物车"
    )
    public Result<String> add(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("添加购物车：{}", shoppingCartDTO);
        shoppingCartService.addShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 购物车商品数量-1
     * @param shoppingCartDTO 购物车数据DTO
     */
    @PostMapping("/sub")
    public Result<String> subShoppingCart(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        shoppingCartService.subShoppingCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查看购物车
     *
     * @return 购物车列表
     */
    @GetMapping("/list")
    @Operation(
            summary = "查看购物车"
    )
    public Result<List<ShoppingCart>> list(){
        List<ShoppingCart> list = shoppingCartService.showShoppingCart();
        return Result.success(list);
    }

    /**
     * 清空购物车
     *
     * @return 清空操作结果
     */
    @DeleteMapping("/clean")
    @Operation(
            summary = "清空购物车"
    )
    public Result<String> cleanShoppingCart(){
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }

}
