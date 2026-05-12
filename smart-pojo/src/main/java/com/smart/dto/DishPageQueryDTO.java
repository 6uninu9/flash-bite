package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DishPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int page;

    private int pageSize;

    private String name;

    //分类id
    private Integer categoryId;

    //状态 0表示禁用 1表示启用
    private Integer status;

}
