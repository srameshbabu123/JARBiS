package com.jarbis.brokerage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main application class for the JARBiS Brokerage Platform. Serves as the entry
 * point for the Spring Boot application.
 */
@SpringBootApplication
@EnableKafka
public class BrokerageApplication {

	public static void main(final String[] args) {
		SpringApplication.run(BrokerageApplication.class, args);
	}
}
