package com.sensors.smartshoeserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartshoeServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartshoeServerApplication.class, args);
	}

}
