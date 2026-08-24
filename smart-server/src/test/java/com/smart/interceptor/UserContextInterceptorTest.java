package com.smart.interceptor;

import com.jayway.jsonpath.JsonPath;
import com.smart.context.BaseContext;
import com.smart.entity.User;
import com.smart.service.DishService;
import com.smart.controller.user.UserController;
import com.smart.properties.JwtProperties;
import com.smart.service.UserService;
import com.smart.utils.JwtUtil;
import com.smart.vo.DishVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserContextInterceptorTest {

    private MockMvc mockMvc;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setUserSecretKeyOrigin("test-user-secret-key-must-be-at-least-32-bytes");
        jwtProperties.setUserTtl(60_000);
        jwtProperties.setUserTokenName("authentication");
        jwtProperties.setAdminSecretKeyOrigin("test-admin-secret-key-must-be-at-least-32-bytes");
        jwtProperties.setAdminTtl(60_000);
        jwtProperties.setAdminTokenName("token");
        jwtProperties.init();

        UserService userService = mock(UserService.class);
        when(userService.wxLogin(any())).thenReturn(User.builder().id(42L).openid("test-openid").build());
        DishService dishService = mock(DishService.class);
        when(dishService.getDishListByCategoryId(1L))
                .thenReturn(List.of(DishVO.builder().id(100L).name("测试菜品").categoryId(1L).build()));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, jwtProperties),
                        new com.smart.controller.user.DishController(dishService),
                        new ProtectedEndpoint())
                .addInterceptors(new UserContextInterceptor(jwtProperties))
                .build();
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeCurrentId();
    }

    @Test
    void loginTokenAuthenticatesProtectedUserEndpoint() throws Exception {
        String loginResponse = mockMvc.perform(post("/user/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"test-login-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(loginResponse, "$.data.token");
        mockMvc.perform(get("/user/test/protected").header("authentication", token))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void forgedIdentityHeaderDoesNotAuthenticateRequest() throws Exception {
        mockMvc.perform(get("/user/test/protected").header("X-User-Id", "42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDishQueryWorksWithoutGateway() throws Exception {
        mockMvc.perform(get("/user/dish/list").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].name").value("测试菜品"));
    }

    @Test
    void adminTokenUsesAdminKeyAndClaim() throws Exception {
        String adminToken = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(),
                Map.of("empId", 9L));

        mockMvc.perform(get("/admin/test/protected").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("9"));
        mockMvc.perform(get("/user/test/protected").header("authentication", adminToken))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProtectedEndpoint {

        @GetMapping({"/user/test/protected", "/admin/test/protected"})
        String currentUserId() {
            return String.valueOf(BaseContext.getCurrentId());
        }
    }
}
