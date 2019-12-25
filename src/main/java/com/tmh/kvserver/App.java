package com.tmh.kvserver;

import java.util.Arrays;

import com.tmh.kvserver.raft.HashMapStateMachine;
import com.tmh.kvserver.raft.StateMachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
@Configuration
@ComponentScan(basePackages =  "com.tmh.kvserver")
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
