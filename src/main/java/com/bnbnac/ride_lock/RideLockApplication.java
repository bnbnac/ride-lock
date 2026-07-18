package com.bnbnac.ride_lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RideLockApplication {

	public static void main(String[] args) {
		SpringApplication.run(RideLockApplication.class, args);
	}

}
