package com.danzzan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DanzzanApplication {

    public static void main(String[] args) {
        SpringApplication.run(DanzzanApplication.class, args);
    }
}
