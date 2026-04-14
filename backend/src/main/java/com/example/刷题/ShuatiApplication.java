package com.example.刷题;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example")
@MapperScan("com.example.刷题.mapper")
public class ShuatiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShuatiApplication.class, args);
    }

}
