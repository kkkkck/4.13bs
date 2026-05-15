package com.example.刷题;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example")
@MapperScan("com.example.刷题.mapper")
public class ShuatiApplication {

    public static void main(String[] args) {
        // main 方法是整个后端程序的“开关”。你答辩时可以这样讲：
        // Spring Boot 从这里启动，然后自动加载 application.yml、Controller、Service、Mapper 等组件。
        // 后端启动入口。运行 mvn spring-boot:run 时，Spring Boot 会从这里开始加载配置、
        // 扫描 Controller/Service/Mapper，并在 8080 端口提供 /api 接口。
        SpringApplication.run(ShuatiApplication.class, args);
    }

}
