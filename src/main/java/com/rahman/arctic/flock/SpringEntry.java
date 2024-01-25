package com.rahman.arctic.flock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.rahman.arctic.flock", "com.rahman.arctic.orca", "com.rahman.arctic.iceberg"})
public class SpringEntry {
	
	public static void main(String[] args) {
		SpringApplication.run(SpringEntry.class, args);
	}
	
}