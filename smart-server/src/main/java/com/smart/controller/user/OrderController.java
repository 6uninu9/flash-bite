package com.smart.controller.user;


import com.smart.dto.OrdersPaymentDTO;
import com.smart.dto.OrdersSubmitDTO;
import com.smart.result.Result;
import com.smart.service.OrderService;
import com.smart.vo.OrderPaymentVO;
import com.smart.vo.OrderSubmitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Tag(name = "C端-订单相关接口")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 下单数据
     * @return 订单确认数据
     */
    @PostMapping("/submit")
    @Operation(
            summary = "用户下单"
    )
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 订单支付数据
     * @return 订单支付结果
     */
    @PutMapping("/payment")
    @Operation(
            summary = "订单支付"
    )
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }
}
