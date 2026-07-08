package com.video.task.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.video.task.scheduler", "com.video.task.worker", "com.video.task.common"})
public class VideoTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoTaskApplication.class, args);
    }
}
