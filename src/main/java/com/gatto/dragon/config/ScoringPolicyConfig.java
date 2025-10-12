package com.gatto.dragon.config;

import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dragon.scoring")
@Setter
public class ScoringPolicyConfig {

    @Bean("scoringPolicy")
    public ScoringPolicy scoringPolicyRouter(
            @Qualifier("expectedValueScoringPolicy") ScoringPolicy ev
    ) {
        return ev;
    }
}
