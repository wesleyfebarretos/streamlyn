package com.streamlyn.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StreamlynApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(StreamlynApiApplication.class, args);
	}

}
