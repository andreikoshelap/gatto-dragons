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

    private String policy = "bankrollAware"; //default

    @Bean("scoringPolicy")
    public ScoringPolicy scoringPolicyRouter(
            @Qualifier("expectedValueScoringPolicy") ScoringPolicy ev,
            @Qualifier("riskAverseScoringPolicy") ScoringPolicy risk,
            @Qualifier("repAwareScoringPolicy") ScoringPolicy rep,
            @Qualifier("bankrollAwareScoringPolicy") ScoringPolicy ba
    ) {
        return switch (policy) {
            case "ev"           -> ev;
            case "riskAverse"   -> risk;
            case "repAware"     -> rep;
            default             -> ba; // fallback
        };
    }
}
