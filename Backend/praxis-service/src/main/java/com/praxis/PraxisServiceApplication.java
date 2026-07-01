package com.praxis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulithic
@EnableScheduling
@SpringBootApplication
public class PraxisServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PraxisServiceApplication.class, args);
	}
}
