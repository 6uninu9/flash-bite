package com.smart.controller.user;

import com.smart.context.BaseContext;
import com.smart.dto.AddressBookDTO;
import com.smart.entity.AddressBook;
import com.smart.result.Result;
import com.smart.service.AddressBookService;
import com.smart.vo.AddressBookVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/addressBook")
@Tag(name = "C端地址簿接口")
public class AddressBookController {


    private final AddressBookService addressBookService;

    @Autowired
    public AddressBookController(AddressBookService addressBookService){
        this.addressBookService = addressBookService;
    }

    /**
     * 查询当前登录用户的所有地址信息
     *
     * @return 地址列表
     */
    @GetMapping("/list")
    @Operation(
            summary = "查询当前登录用户的所有地址信息"
    )
    public Result<List<AddressBookVO>> list() {
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());
        List<AddressBookVO> list = addressBookService.list(addressBook);
        return Result.success(list);
    }

    /**
     * 新增地址
     *
     * @param addressBookDTO 新增的地址信息
     * @return 添加操作结果
     */
    @PostMapping
    @Operation(
            summary = "新增地址"
    )
    public Result<String> save(@RequestBody AddressBookDTO addressBookDTO) {
        addressBookService.save(addressBookDTO);
        return Result.success();
    }

    /**
     * 根据id查询地址
     *
     * @param id 查询的地址id
     * @return 查询到的地址信息
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "根据id查询地址"
    )
    public Result<AddressBookVO> getById(@PathVariable Long id) {
        AddressBookVO addressBook = addressBookService.getById(id);
        return Result.success(addressBook);
    }

    /**
     * 根据id修改地址
     *
     * @param addressBookDTO 修改的地址
     * @return 修改操作结果
     */
    @PutMapping
    @Operation(
            summary = "根据id修改地址"
    )
    public Result<String> update(@RequestBody AddressBookDTO  addressBookDTO) {
        addressBookService.update(addressBookDTO);
        return Result.success();
    }

    /**
     * 设置默认地址
     *
     * @param addressBookDTO 设置的地址信息
     * @return 设置操作结果
     */
    @PutMapping("/default")
    @Operation(
            summary = "设置默认地址"
    )
    public Result<String> setDefault(@RequestBody AddressBookDTO addressBookDTO) {
        addressBookService.setDefault(addressBookDTO);
        return Result.success();
    }

    /**
     * 根据id删除地址
     *
     * @param id 删除的地址id
     * @return 删除操作结果
     */
    @DeleteMapping("/")
    @Operation(
            summary = "根据id删除地址"
    )
    public Result<String> deleteById(Long id) {
        addressBookService.deleteById(id);
        return Result.success();
    }

    /**
     * 查询默认地址
     */
    @GetMapping("default")
    @Operation(
            summary = "查询默认地址"
    )
    public Result<AddressBookVO> getDefault() {
        //SQL:select * from address_book where user_id = ? and is_default = 1
        AddressBook addressBook = new AddressBook();
        addressBook.setIsDefault(1);
        addressBook.setUserId(BaseContext.getCurrentId());
        List<AddressBookVO> list = addressBookService.list(addressBook);

        if (list != null && list.size() == 1) {
            return Result.success(list.getFirst());
        }

        return Result.error("没有查询到默认地址");
    }

}
