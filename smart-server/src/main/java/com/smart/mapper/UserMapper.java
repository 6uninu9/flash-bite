package com.smart.mapper;

import com.smart.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid user openid
     * @return user
     */
    @Select("select * from user where openid = #{openid}")
    User getOpenid(String openid);

    /**
     * 插入用户数据
     * @param user user
     */
    void insert(User user);

    /**
     * 根据id查询用户
     * @param userId 用户id
     * @return user
     */
    @Select("select * from user where id = #{userId}")
    User getById(Long userId);
}
