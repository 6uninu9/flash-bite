package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "employeeLoginAspect", description = "员工登录时传递的数据模型")
public class EmployeeLoginDTO implements Serializable {  //DTO是专门用来进行数据传输，比如接收前端传来的表单数据

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度需在2-20个字符之间")
    @Schema(name = "username", description = "用户名")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在6-20个字符之间")
    @Schema(name = "password", description = "密码")
    private String password;
}
