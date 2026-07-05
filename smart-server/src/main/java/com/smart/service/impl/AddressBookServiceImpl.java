package com.smart.service.impl;

import com.smart.context.BaseContext;
import com.smart.dto.AddressBookDTO;
import com.smart.entity.AddressBook;
import com.smart.mapper.AddressBookMapper;
import com.smart.service.AddressBookService;
import com.smart.vo.AddressBookVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {

    private final AddressBookMapper addressBookMapper;

    public AddressBookServiceImpl(AddressBookMapper addressBookMapper) {
        this.addressBookMapper = addressBookMapper;
    }

    /**
     * 条件查询
     *
     * @param addressBook 查询条件
     * @return 地址数据列表
     */
    @Override
    public List<AddressBookVO> list(AddressBook addressBook) {
        List<AddressBook> addressBookList = addressBookMapper.list(addressBook);
        return addressBookList.stream().map(item -> {
            AddressBookVO vo = new AddressBookVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).toList();
    }

    /**
     * 新增地址
     *
     * @param addressBookDTO 新增的地址
     */
    @Override
    public void save(AddressBookDTO addressBookDTO) {
        AddressBook addressBook = new AddressBook();
        BeanUtils.copyProperties(addressBookDTO, addressBook);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setCreateTime(LocalDateTime.now());
        addressBook.setUpdateTime(LocalDateTime.now());
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id 查询的地址id
     * @return 查询到的地址
     */
    @Override
    public AddressBookVO getById(Long id) {
        AddressBook addressBook = addressBookMapper.getById(id);
        AddressBookVO vo = new AddressBookVO();
        BeanUtils.copyProperties(addressBook, vo);
        return vo;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBookDTO 修改的地址信息
     */
    @Override
    public void update(AddressBookDTO addressBookDTO) {
        AddressBook addressBook = new AddressBook();
        BeanUtils.copyProperties(addressBookDTO, addressBook);
        addressBook.setUpdateTime(LocalDateTime.now());
        addressBookMapper.update(addressBook);
    }

    /**
     * 设置默认地址
     *
     * @param addressBookDTO 设置的默认地址信息
     */
    @Override
    @Transactional
    public void setDefault(AddressBookDTO addressBookDTO) {
        // 1.将当前用户的所有地址修改为非默认地址
        AddressBook addressBook = new AddressBook();
        BeanUtils.copyProperties(addressBookDTO, addressBook);
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setUpdateTime(LocalDateTime.now());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        // 2. 将当前地址改为默认地址
        addressBook.setIsDefault(1);
        addressBookMapper.update(addressBook);
    }

    /**
     * 根据id删除地址
     *
     * @param id 删除的地址id
     */
    @Override
    public void deleteById(Long id) {
        addressBookMapper.deleteById(id);
    }
}