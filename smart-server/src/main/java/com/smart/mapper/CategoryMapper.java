package com.smart.mapper;

import com.smart.entity.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 根据类型查询分类
     * @param type 分类类型
     * @return 分类列表
     */
    List<Category> list(Integer type);

    /**
     * 查询所有分类id
     * @return 分类id列表
     */
    @Select("SELECT id FROM category")
    List<String> listAllIds();
}
