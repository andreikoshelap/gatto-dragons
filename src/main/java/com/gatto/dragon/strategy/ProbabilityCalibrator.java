package com.gatto.dragon.strategy;

import com.gatto.dragon.config.ProbabilityPriorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns label-based probabilities into calibrated numbers using Bayesian smoothing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProbabilityCalibrator {

    private final ProbabilityPriorProperties priorProps;

    // stats[label] = [successes, attempts]
    private final ConcurrentHashMap<String, double[]> stats = new ConcurrentHashMap<>();

    public double calibratedProb(String label) {
        String key = label == null ? "" : label.toLowerCase();
        double oldValue = priorProps.getPriors().getOrDefault(key, 0.5);
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
    public List<Row> snapshot() {
        Set<String> all = new TreeSet<>();
        all.addAll(priorProps.getPriors().keySet());
        all.addAll(stats.keySet());

        List<Row> out = new ArrayList<>();
        for (String k : all) {
            double prior = priorProps.getPriors().getOrDefault(k, 0.5);
            double[] s = stats.getOrDefault(k, new double[]{0,0});
            double succ = s[0], att = s[1];
            double empirical = att > 0 ? (succ/att) : Double.NaN;
            double calib = calibratedProb(k);
            out.add(new Row(k, prior, succ, att, empirical, calib));
        }
        out.sort(Comparator.comparing(r -> r.calibrated));
        return out;
    }

    public void dump(String gameId) {
        var rows = snapshot();
        log.info("=== Probability mapping after game {} ===", gameId);
        log.info(String.format("%-30s | %-5s | %9s | %9s | %9s | %9s",
                "label","prior","successes","attempts","empirical","calibr."));
        log.info("--------------------+-------+-----------+-----------+-----------+-----------");
        for (Row r : rows) {
            log.info(String.format("%-30s | %.2f  | %9.0f | %9.0f | %9s | %.3f",
                    r.label, r.prior, r.successes, r.attempts,
                    Double.isNaN(r.empirical) ? "—" : String.format("%.3f", r.empirical),
                    r.calibrated));
        }
    }

    public record Row(String label, double prior, double successes, double attempts,
                      double empirical, double calibrated) {}
}

