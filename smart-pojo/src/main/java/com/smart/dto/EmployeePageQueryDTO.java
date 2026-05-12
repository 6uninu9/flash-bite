package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class EmployeePageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //员工姓名
    private String name;

    //页码
    private int page;

    //每页显示记录数
    private int pageSize;

}
