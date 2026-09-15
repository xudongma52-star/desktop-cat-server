package com.desktopcat.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DesktopCatApplication {
    public static void main(String[] args) {
        SpringApplication.run(DesktopCatApplication.class, args);
    }
}
