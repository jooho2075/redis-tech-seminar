package dev.woorifis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync; // 추가
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync // 핵심: 비동기 활성화
@EnableScheduling
@SpringBootApplication
public class RedisArchitectureApplication {
	public static void main(String[] args) {
		SpringApplication.run(RedisArchitectureApplication.class, args);
	}
}