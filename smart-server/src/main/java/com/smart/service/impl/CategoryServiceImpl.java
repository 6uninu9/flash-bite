package com.smart.service.impl;

import com.smart.entity.Category;
import com.smart.mapper.CategoryMapper;
import com.smart.service.BloomFilterDataService;
import com.smart.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService, BloomFilterDataService {


    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 根据类型查询分类
     * @param type 分类类型
     * @return 分类列表
     */
    @Override
    public List<Category> list(Integer type) {
        return categoryMapper.list(type);
    }

    /**
     * 获取所有分类id
     * @return 分类id列表
     */
    @Override
    public List<String> getKey() {
        return categoryMapper.listAllIds();
    }
}
