package com.smart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling//开启定时任务
@SpringBootApplication
public class SmartServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartServerApplication.class, args);
    }

}
