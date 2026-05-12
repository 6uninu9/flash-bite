package com.smart.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
}
