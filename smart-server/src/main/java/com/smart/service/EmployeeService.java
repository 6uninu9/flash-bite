package com.smart.service;

import com.smart.dto.EmployeeLoginDTO;
import com.smart.vo.EmployeeLoginVO;

public interface EmployeeService {
    /**
     * 员工登录
     * @param employeeLoginDTO 登录信息
     * @return 登录结果
     */
    EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO);
}
