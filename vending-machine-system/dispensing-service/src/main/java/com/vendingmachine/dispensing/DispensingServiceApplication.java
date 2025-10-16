package com.vendingmachine.dispensing;

import com.vendingmachine.dispensing.dispensing.HardwareStatusService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
    "com.vendingmachine.dispensing",
    "com.vendingmachine.common"
})
public class DispensingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DispensingServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner initializeHardware(HardwareStatusService hardwareStatusService) {
        return args -> hardwareStatusService.initializeHardwareComponents();
    }
}