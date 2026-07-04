package com.smart.service.impl;

import com.smart.constant.JwtClaimsConstant;
import com.smart.constant.MessageConstant;
import com.smart.dto.EmployeeLoginDTO;
import com.smart.entity.Employee;
import com.smart.exception.AccountLockedException;
import com.smart.exception.AccountNotFoundException;
import com.smart.exception.PasswordErrorException;
import com.smart.mapper.EmployeeMapper;
import com.smart.properties.JwtProperties;
import com.smart.service.EmployeeService;
import com.smart.utils.JwtUtil;
import com.smart.vo.EmployeeLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeMapper employeeMapper;

    private final JwtProperties jwtProperties;

    public EmployeeServiceImpl(EmployeeMapper employeeMapper, JwtProperties jwtProperties) {
        this.employeeMapper = employeeMapper;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 员工登录
     *
     * @param employeeLoginDTO 登录信息
     * @return 登录结果
     */
    @Override
    public EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO) {
        // 1. 根据用户名查询员工
        Employee employee = employeeMapper.getByUsername(employeeLoginDTO.getUsername());
        // 2. 判断员工是否存在
        if (employee == null) {
            log.info("员工登录失败：用户不存在");
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        // 3. 判断密码是否正确
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(employeeLoginDTO.getPassword(), employee.getPassword())) {
            log.info("员工登录失败：密码错误");
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        // 4. 判断员工状态是否可用
        if (employee.getStatus() == 0) {
            log.info("员工登录失败：账号被锁定");
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 5. 构造 JWT 令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.MERCHANT_ID, employee.getMerchantId()); // 存入 merchantId
        claims.put(JwtClaimsConstant.ROLE, employee.getRole());             // 存入 role
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        // 6. 返回 VO
        return EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .role(employee.getRole())
                .token(token)
                .build();
    }
}
