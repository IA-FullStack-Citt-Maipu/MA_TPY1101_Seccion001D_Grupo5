package com.panol_project.backendpanol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendpanolApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendpanolApplication.class, args);
	}

}
