package com.smart.controller.user;

import com.smart.entity.Category;
import com.smart.result.Result;
import com.smart.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
@Tag(name = "C端-分类接口")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询分类
     * @param type 分类类型（此类型是指菜品与套餐，留有此字段方便后续拓展套餐服务）
     * @return 分类数据
     */
    @GetMapping("/list")
    @Operation(
           summary = "根据类型查询分类"
    )
    public Result<List<Category>> list(Integer type) {
        List<Category> list = categoryService.list(type);
        return Result.success(list);
    }
}
