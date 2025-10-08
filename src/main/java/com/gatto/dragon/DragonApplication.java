package com.gatto.dragon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DragonApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(DragonApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.run(args);
  }
}
