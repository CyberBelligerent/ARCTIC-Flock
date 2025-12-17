package com.rahman.arctic.flock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.rahman.arctic"})
//@EnableJpaRepositories(basePackages = {"com.rahman.arctic.iceberg.repos", "com.rahman.arctic.polarbear.repos", "com.rahman.arctic.shard.repos"})
//@EntityScan(basePackages = {"com.rahman.arctic.iceberg.objects", "com.rahman.arctic.polarbear.objects", "com.rahman.arctic.shard.objects"})
@EnableJpaRepositories(basePackages = {"com.rahman.arctic"})
@EntityScan(basePackages = {"com.rahman.arctic"})
public class SpringEntry {
	
	public static void main(String[] args) {
		SpringApplication.run(SpringEntry.class, args);
	}
	
}