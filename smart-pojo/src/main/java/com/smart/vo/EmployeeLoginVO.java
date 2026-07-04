package com.smart.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "employeeLoginReturn", description = "员工登录返回的数据格式") //在接口文档中描述模型与属性
public class EmployeeLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "id", description = "主键值")
    private Long id;

    @Schema(name = "username", description = "用户名")
    private String userName;

    @Schema(name = "name", description = "姓名")
    private String name;

    @Schema(name = "role", description = "角色")
    private String role; // 方便前端做按钮级权限隐藏

    @Schema(name = "token", description = "jwt令牌")
    private String token;

}
