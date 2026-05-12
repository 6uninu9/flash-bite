package com.smart.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象......");
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        //设置redis连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // 创建字符串序列化器，统一解决乱码
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // 创建JSON序列化器，解决Hash的field的乱码
//        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer
//                = new Jackson2JsonRedisSerializer<>(Object.class);
        // 1.设置key的序列化器
        redisTemplate.setKeySerializer(stringSerializer);
        // 2.设置value的序列化器
        redisTemplate.setValueSerializer(stringSerializer);
        // 3.设置Hash的field的序列化器
        redisTemplate.setHashKeySerializer(stringSerializer);
        // 4.设置Hash的value的序列化器
        redisTemplate.setHashValueSerializer(stringSerializer);
        // 5.让配置生效
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
