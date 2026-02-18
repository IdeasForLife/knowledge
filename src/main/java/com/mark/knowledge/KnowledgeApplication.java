package com.mark.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableJpaRepositories
@EnableConfigurationProperties
@ComponentScan(basePackages = {"com.mark.knowledge"})
public class KnowledgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnowledgeApplication.class, args);
	}

}
