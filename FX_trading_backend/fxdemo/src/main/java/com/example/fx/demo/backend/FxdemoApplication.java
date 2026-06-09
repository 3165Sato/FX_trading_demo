package com.example.fx.demo.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FxdemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(FxdemoApplication.class, args);
	}

}
