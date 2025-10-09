package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Bankroll-aware EV scoring:
 * - base EV: p * reward * urgency * lifePenalty
 * - on one life:
 *   - if gold < potionCost: multiply by survival=p (fail likely ends the game)
 *   - else: survival = p + (1-p)*q; subtract expected potion cost (1-p)*potionCost*gamma
 */
@Component("bankrollAwareScoringPolicy")
@RequiredArgsConstructor
public class BankrollAwareScoringPolicy implements ScoringPolicy {

    private final ProbabilityCalibrator calibrator;
    private final BankrollParams params;

    @Override
    public double score(Message message, ScoringContext ctx) {
        double p = calibrator.calibratedProb(message.probability());
        double urgency = 1.0 + Math.max(0, 5 - message.expiresIn()) * 0.1;
        double lifePenalty = ctx.lives() <= 1 ? params.lifePenaltyAtOne : 1.0;
        double base = p * Math.max(0, message.reward()) * urgency * lifePenalty;

        if (ctx.lives() > 1) return base;

        int potionCost = ctx.potionCostHint() != null ? ctx.potionCostHint() : params.potionCost;
        boolean canHeal = ctx.gold() >= potionCost;

        if (!canHeal) {
            return base * p; // fail = конец, ценим только успех
        } else {
            double survival = p + (1 - p) * params.qSurviveOnFail;
            double expectedGoldSpend = (1 - p) * potionCost;
            double goldPenalty = expectedGoldSpend * params.gammaGoldToScore;
            return Math.max(0.0, base * survival - goldPenalty);
        }
    }

    @Setter
    @Configuration
    @ConfigurationProperties(prefix = "dragon.bankroll")
    public static class BankrollParams {
        // setters for @ConfigurationProperties
        /** lifePenalty multiplier when lives==1 (as in your EV, default 0.8) */
        public double lifePenaltyAtOne = 0.8;
        /** Potion price (heuristic/average), use the real one if you know it */
        public int potionCost = 100;
        /** How often a failure with a heal is survived (0..1) */
        public double qSurviveOnFail = 0.7;
        /** Conversion of gold into "points": 1 gold == gamma score units */
        public double gammaGoldToScore = 0.1;

    }
}
