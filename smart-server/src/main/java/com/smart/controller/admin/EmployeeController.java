package com.smart.controller.admin;

import com.smart.dto.EmployeeLoginDTO;
import com.smart.result.Result;
import com.smart.service.EmployeeService;
import com.smart.vo.EmployeeLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Tag(name = "员工相关接口") //用来对接口文档中的api进行分组
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }


    /**
     * 登录
     * @param employeeLoginDTO 登录信息
     * @return 登录结果
     */
    @PostMapping("/login")
    @Operation(
            summary = "员工登录",
            description = "根据用户名和密码进行员工身份验证，验证通过后返回包含用户信息和 JWT 令牌的响应"
    )
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        EmployeeLoginVO employeeLoginVO = employeeService.login(employeeLoginDTO);

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     * 前端收到响应后，清除本地存储的 JWT 令牌，并跳转到登录页面
     *
     * @return  Result
     */
    @PostMapping("/logout")
    @Operation(
            summary = "员工退出"
    )
    public Result<String> logout() {
        return Result.success();
    }
}
