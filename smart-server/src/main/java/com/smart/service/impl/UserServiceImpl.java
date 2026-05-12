package com.smart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.smart.constant.MessageConstant;
import com.smart.dto.UserLoginDTO;
import com.smart.entity.User;
import com.smart.exception.LoginFailedException;
import com.smart.mapper.UserMapper;
import com.smart.properties.WeChatProperties;
import com.smart.service.UserService;
import com.smart.utils.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String WX_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";

    private final WeChatProperties weChatProperties;

    private final UserMapper userMapper;

    public UserServiceImpl(WeChatProperties weChatProperties, UserMapper userMapper) {
        this.weChatProperties = weChatProperties;
        this.userMapper = userMapper;
    }


    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        //调用微信接口，获取微信登录的openid
        String openid = getWxOpenId(userLoginDTO.getCode());

        //将上一步通过 HTTP 请求获取到的 JSON 字符串格式的微信登录响应数据通过parseObject解析为一个对象，
        //方便从中提取出 openid 字段作为当前登录用户的唯一标识
        JSONObject jsonObject = JSON.parseObject(openid);
        openid = jsonObject.getString("openid");

        log.info("微信登录openid：{}", openid);

        //判断openid是否为空，若为空则表示登录失败，抛出异常
        if (openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        //若不为空，，则判断是否是新用户，即查询数据表中是否存在openid
        User user = userMapper.getOpenid(openid);

        //若是新用户，则插入数据并返回用户id，自动完成注册
        if (user == null){
            user = User.builder()
                    .openid(openid)
                    .createTime(LocalDateTime.now())
                    .build();

            userMapper.insert(user);
        }

        return user;
    }

    /**
     * 调用微信接口，获取微信登录的openid
     * @param code 微信登录成功后，微信会返回一个code，通过code可以获取微信登录的openid
     * @return openid
     */
    private String getWxOpenId(String code) {
        Map<String, String> map = new  HashMap<>();
        map.put("appid", weChatProperties.getAppid());
        map.put("secret", weChatProperties.getSecret());
        map.put("js_code", code);
        map.put("grant_type", "authorization_code");

        return HttpClientUtil.doGet(WX_LOGIN, map);
    }
}
