package com.smart.mapper;

import com.smart.entity.AddressBook;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AddressBookMapper {

    /**
     * 条件查询
     * @param addressBook 查询条件
     * @return 地址列表
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 新增
     * @param addressBook 新增的地址信息
     */
    @Insert("insert into address_book" +
            "        (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code," +
            "         district_name, detail, label, is_default, create_time, update_time)" +
            "        values (#{userId}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault}, #{createTime}, #{updateTime})")
    void insert(AddressBook addressBook);

    /**
     * 根据id查询
     * @param id 查询的地址id
     * @return 地址信息
     */
    @Select("select id, user_id, consignee, sex, phone," +
            " province_code, province_name, city_code, city_name," +
            " district_code, district_name, detail, label, is_default, create_time, update_time" +
            " from address_book where id = #{id}")
    AddressBook getById(Long id);

    /**
     * 根据id修改
     * @param addressBook 修改的地址信息
     */
    void update(AddressBook addressBook);

    /**
     * 根据 用户id修改 是否默认地址
     * @param addressBook 修改的地址信息
     */
    @Update("update address_book set is_default = #{isDefault}, update_time = #{updateTime} where user_id = #{userId}")
    void updateIsDefaultByUserId(AddressBook addressBook);

    /**
     * 根据id删除地址
     * @param id 删除的id
     */
    @Delete("delete from address_book where id = #{id}")
    void deleteById(Long id);
}