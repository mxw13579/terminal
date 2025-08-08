package com.fufu.terminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot应用程序主类
 * 启动SSH终端服务应用
 *
 * @author lizelin
 */
@SpringBootApplication
@EnableScheduling
public class TerminalApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerminalApplication.class, args);
        System.out.println("Terminal Application Started");
    }

}
