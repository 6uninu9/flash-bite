package com.smart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(name = "Employee", description = "操作员工时传递的数据模型")
public class EmployeeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(name = "id",description = "主键")
    private Long id;

    @Schema(name = "username",description = "用户名")
    private String username;

    @Schema(name = "name",description = "姓名")
    private String name;

    @Schema(name = "phone",description = "手机号")
    private String phone;

    @Schema(name = "sex",description = "性别")
    private String sex;

    @Schema(name = "idNumber",description = "身份证号")
    private String idNumber;

}
