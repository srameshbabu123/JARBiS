package com.jarbis.brokerage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the JARBiS Brokerage Platform. Serves as the entry
 * point for the Spring Boot application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableKafka
@EnableScheduling
public class BrokerageApplication {

	public static void main(final String[] args) {
		SpringApplication.run(BrokerageApplication.class, args);
	}
}
