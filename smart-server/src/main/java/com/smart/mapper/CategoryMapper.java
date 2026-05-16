package com.smart.mapper;

import com.smart.entity.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 根据类型查询分类
     * @param type
     * @return
     */
    List<Category> list(Integer type);

    /**
     * 查询所有分类id
     * @return
     */
    @Select("SELECT id FROM category")
    List<String> listAllIds();
}
