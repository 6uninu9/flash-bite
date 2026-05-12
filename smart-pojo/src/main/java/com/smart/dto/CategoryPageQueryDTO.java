package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class CategoryPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    //页码
    private int page;

    //每页记录数
    private int pageSize;

    //分类名称
    private String name;

    //分类类型 1菜品分类  2套餐分类
    private Integer type;

}
