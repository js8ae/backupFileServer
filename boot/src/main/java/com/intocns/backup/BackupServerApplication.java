package com.intocns.backup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackupServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackupServerApplication.class, args);
    }
}
