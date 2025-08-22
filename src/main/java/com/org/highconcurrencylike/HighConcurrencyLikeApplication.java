package com.org.highconcurrencylike;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.org.highconcurrencylike.mapper")
public class HighConcurrencyLikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighConcurrencyLikeApplication.class, args);
    }

}
