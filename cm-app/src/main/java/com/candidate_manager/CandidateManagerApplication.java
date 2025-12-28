package com.candidate_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CandidateManagerApplication {
	public static void main(String[] args) {
		SpringApplication.run(CandidateManagerApplication.class, args);
	}
}
