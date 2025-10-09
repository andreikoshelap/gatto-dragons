package com.gatto.dragon.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Configuration
@ConfigurationProperties(prefix = "dragon.heal")
public class HealParams {
    public int maxPotionPrice = 120;
}
