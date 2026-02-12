package com.bx.implatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bx.implatform.mapper")
public class RtcApp {

    public static void main(String[] args) {
        SpringApplication.run(RtcApp.class, args);
    }
}
