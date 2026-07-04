package com.smart.mapper;

import com.smart.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmployeeMapper {
    /**
     * 根据用户名查询员工
     * @param username 用户名
     * @return 员工
     */
    @Select("SELECT id, username, name, password, phone, sex, id_number, status, merchant_id, role, " +
            "create_time, update_time, create_user, update_user " +
            "FROM employee " +
            "WHERE username = #{username}")
    Employee getByUsername(String username);
}
