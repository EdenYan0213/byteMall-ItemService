package com.yccc.bytemall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yccc.bytemall.mapper")
public class BytemallApplication {

    public static void main(String[] args) {
        SpringApplication.run(BytemallApplication.class, args);
    }

}
