package com.gatto.dragon;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class DragonApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(DragonApplication.class).web(WebApplicationType.NONE).run(args);
  }
}
