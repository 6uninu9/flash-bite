package com.smart.controller.user;

import com.smart.constant.JwtClaimsConstant;
import com.smart.dto.UserLoginDTO;
import com.smart.entity.User;
import com.smart.properties.JwtProperties;
import com.smart.result.Result;
import com.smart.service.UserService;
import com.smart.utils.JwtUtil;
import com.smart.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Slf4j
@Tag(name = "C端用户接口")
public class UserController {

    private final UserService userService;

    private final JwtProperties jwtProperties;

    public UserController(UserService userService, JwtProperties jwtProperties) {
        this.userService = userService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 微信登录
     * @param userLoginDTO 微信登录参数
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(
            summary = "微信用户登录"
    )
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO){
        log.info("微信用户登录：{}", userLoginDTO.getCode());

        //微信登录
        User user = userService.wxLogin(userLoginDTO);

        //获取jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey()
                , jwtProperties.getUserTtl()
                , claims);

        //将值封装进UserLoginVO中
        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();

        return Result.success(userLoginVO);
    }
}
