package com.javaedge.diners;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.javaedge.diners.mapper")
@SpringBootApplication
public class DinersApplication {

    public static void main(String[] args) {
        SpringApplication.run(DinersApplication.class, args);
    }

}
