package com.javaedge.follow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author apple
 */
@MapperScan("com.javaedge.follow.mapper")
@SpringBootApplication
public class FollowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FollowApplication.class, args);
    }

}