package com.gatto.dragon.strategy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns label-based probabilities into calibrated numbers using Bayesian smoothing.
 */
@Component
public class ProbabilityCalibrator {

    // Prior probabilities per label (lowercase)
    private static final Map<String, Double> PRIOR;
    static {
        var m = new java.util.LinkedHashMap<String, Double>();
        m.put("sure thing", 0.95);
        m.put("piece of cake", 0.90);
        m.put("walk in the park", 0.80);
        m.put("quite likely", 0.70);
        m.put("hmmm....", 0.60);
        m.put("gamble", 0.50);
        m.put("risky", 0.40);
        m.put("rather detrimental", 0.20);
        m.put("playing with fire", 0.15);
        m.put("suicide mission", 0.10);
        m.put("impossible", 0.05);
        PRIOR = java.util.Collections.unmodifiableMap(m);
    }

    // stats[label] = [successes, attempts]
    private final ConcurrentHashMap<String, double[]> stats = new ConcurrentHashMap<>();

    public double calibratedProb(String label) {
        String key = label == null ? "" : label.toLowerCase();
        double oldValue = PRIOR.getOrDefault(key, 0.5);
        double[] s = stats.get(key);
        if (s == null) return oldValue;
        double result = s[0], attempt = s[1];
        // smoothing strength: smaller = faster adaptation
        double alpha = 10.0;
        return (result + alpha * oldValue) / (attempt + alpha);
    }

    public void recordOutcome(String label, boolean success) {
        String k = label == null ? "" : label.toLowerCase();
        stats.compute(k, (kk, v) -> {
            if (v == null) v = new double[2];
            if (success) v[0]++;
            v[1]++;
            return v;
        });
    }
}

