package com.tmh.kvserver;

import com.tmh.kvserver.raft.HashMapStateMachine;
import com.tmh.kvserver.raft.StateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

@Slf4j
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        log.info("Let's inspect the beans provided by Spring Boot:");
        ApplicationContext ctx = SpringApplication.run(App.class, args);
        String[] beanNames = ctx.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            log.info(beanName);
        }
    }

    @Bean
    public StateMachine shareStateMachine() {
        return new HashMapStateMachine();
    }

}
