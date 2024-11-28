package com.mirror.hoj.codesandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.mirror")
@SpringBootApplication
public class HojCodeSandboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(HojCodeSandboxApplication.class, args);
    }

}
