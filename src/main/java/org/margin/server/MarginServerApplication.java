package org.margin.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarginServerApplication {
	static void main(String[] args) {
		SpringApplication.run(MarginServerApplication.class, args);
	}
}
