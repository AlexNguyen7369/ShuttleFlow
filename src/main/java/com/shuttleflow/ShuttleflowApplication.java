package com.shuttleflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShuttleflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShuttleflowApplication.class, args);
    }
}
