package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "employeeLoginAspect", description = "员工登录时传递的数据模型")
public class EmployeeLoginDTO implements Serializable {  //DTO是专门用来进行数据传输，比如接收前端传来的表单数据

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "username", description = "用户名")
    private String username;

    @Schema(name = "password", description = "密码")
    private String password;
}
