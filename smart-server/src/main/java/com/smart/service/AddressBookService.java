package com.smart.service;

import com.smart.entity.AddressBook;

import java.util.List;

public interface AddressBookService {

    /**
     * 条件查询
     * @param addressBook 查询条件
     * @return 购物车数据列表
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 新增地址
     * @param addressBook 购物车数据
     */
    void save(AddressBook addressBook);

    /**
     * 根据id查询地址
     * @param id 地址id
     * @return 地址数据
     */
    AddressBook getById(Long id);

    /**
     * 根据id修改地址
     * @param addressBook 购物车数据
     */
    void update(AddressBook addressBook);

    /**
     * 根据用户id修改是否默认地址
     * @param addressBook 购物车数据
     */
    void setDefault(AddressBook addressBook);

    /**
     * 根据id删除地址
     * @param id 地址id
     */
    void deleteById(Long id);

}
