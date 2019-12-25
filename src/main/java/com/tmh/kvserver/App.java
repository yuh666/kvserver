package com.tmh.kvserver;

import com.tmh.kvserver.raft.HashMapStateMachine;
import com.tmh.kvserver.raft.StateMachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
@ComponentScan(basePackages =  "com.tmh.kvserver")
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	@Bean
	public StateMachine shareStateMachine() {
		return new HashMapStateMachine();
	}

}
