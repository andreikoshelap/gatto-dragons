// com.gatto.dragon.config.ProbabilityPriorProperties
package com.gatto.dragon.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Getter @Setter
@ConfigurationProperties(prefix = "dragon.probability")
public class ProbabilityPriorProperties {

    // key -> prior in [0,1]
    private Map<String, Double> priors = defaultPriors();

    // normalize to lowercase once
    public Map<String, Double> normalized() {
        Map<String, Double> out = new LinkedHashMap<>();
        priors.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT), v));
        return out;
    }

    private static Map<String, Double> defaultPriors() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("sure thing", 0.95);
        m.put("piece of cake", 0.90);
        m.put("walk in the park", 0.80);
        m.put("quite likely", 0.70);
        m.put("hmmm....", 0.60);
        m.put("gamble", 0.50);
        m.put("risky", 0.40);
        m.put("rather detrimental", 0.20);
        m.put("playing with fire", 0.10);
        m.put("suicide mission", 0.05);
        m.put("impossible", 0.01);
        return m;
    }
}
