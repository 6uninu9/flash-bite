package com.smart.service;


import com.smart.dto.UserLoginDTO;
import com.smart.entity.User;

public interface UserService {
    User wxLogin(UserLoginDTO userLoginDTO);
}
