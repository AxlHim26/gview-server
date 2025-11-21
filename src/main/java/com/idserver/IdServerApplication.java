package com.idserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Enable scheduled tasks for stale session checking
public class IdServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdServerApplication.class, args);
	}

}

