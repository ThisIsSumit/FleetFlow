package com.fleetflow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(considerNestedRepositories = true)
public class FleetFlowApplication {
  public static void main(String[] args) { SpringApplication.run(FleetFlowApplication.class, args); }
}
