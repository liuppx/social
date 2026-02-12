package com.bx.implatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bx.implatform.mapper")
public class Web3IdentityApp {

    public static void main(String[] args) {
        SpringApplication.run(Web3IdentityApp.class, args);
    }
}
