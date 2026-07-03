package com.insurance.pricingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PricingserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PricingserviceApplication.class, args);
	}

}
