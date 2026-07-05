package com.smart.service;

import com.smart.dto.AddressBookDTO;
import com.smart.entity.AddressBook;
import com.smart.vo.AddressBookVO;

import java.util.List;

public interface AddressBookService {

    /**
     * 条件查询
     * @param addressBook 查询条件
     * @return 地址数据列表
     */
    List<AddressBookVO> list(AddressBook addressBook);

    /**
     * 新增地址
     * @param addressBookDTO 地址信息
     */
    void save(AddressBookDTO addressBookDTO);

    /**
     * 根据id查询地址
     * @param id 地址id
     * @return 地址数据
     */
    AddressBookVO getById(Long id);

    /**
     * 根据id修改地址
     * @param addressBookDTO 地址信息
     */
    void update(AddressBookDTO addressBookDTO);

    /**
     * 设置默认地址
     * @param addressBookDTO 地址
     */
    void setDefault(AddressBookDTO addressBookDTO);

    /**
     * 根据id删除地址
     * @param id 地址id
     */
    void deleteById(Long id);
}