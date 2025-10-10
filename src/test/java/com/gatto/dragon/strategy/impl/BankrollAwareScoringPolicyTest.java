package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.strategy.ProbabilityCalibrator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankrollAwareScoringPolicyTest extends AbstractScoringTestBase {

    // --- simple stub calibrator: returns p from label map or default 0.5
    private static class StubCalibrator extends ProbabilityCalibrator {
        private final double p;
        StubCalibrator(double p) { this.p = p; }
        @Override public double calibratedProb(String label) { return p; }
        @Override public void recordOutcome(String label, boolean success) { /* no-op */ }
        @Override public void dump(String gameId) { /* no-op */ }
    }

    private static BankrollAwareScoringPolicy policy(double p,
                                                     double lifePenaltyAtOne,
                                                     int potionCost,
                                                     double qSurviveOnFail,
                                                     double gamma) {
        var params = new BankrollAwareScoringPolicy.BankrollParams();
        params.lifePenaltyAtOne = lifePenaltyAtOne;
        params.potionCost = potionCost;
        params.qSurviveOnFail = qSurviveOnFail;
        params.gammaGoldToScore = gamma;
        return new BankrollAwareScoringPolicy(new StubCalibrator(p), params);
    }

    @Test
    void lives_gt_1_returns_base_ev() {
        double p = 0.7;
        int reward = 100;
        int expires = 2; // urgency = 1 + (5-2)*0.1 = 1.3
        double lifePenaltyAtOne = 0.8;

        var policy = policy(p, lifePenaltyAtOne, 100, 0.7, 0.1);
        var m = msg("quite likely", reward, expires);
        var c = sc(2, 200, 1);

        double urgency = 1.0 + Math.max(0, 5 - expires) * 0.1; // 1.3
        double base = p * reward * urgency * 1.0; // lifePenalty=1.0 when lives>1

        double actual = policy.score(m, c);
        assertEquals(base, actual, 1e-9);
    }

    @Test
    void one_life_cannot_heal_multiplies_by_survival_p() {
        double p = 0.6;
        int reward = 120;
        int expires = 5; // urgency = 1.0
        double lifePenaltyAtOne = 0.8;
        int potionCost = 100;

        var policy = policy(p, lifePenaltyAtOne, potionCost, 0.7, 0.1);
        var m = msg("hmm....", reward, expires);
        var c = sc(1, 50, 2); // gold < potionCost → cannot heal

        double urgency = 1.0;
        double base = p * reward * urgency * lifePenaltyAtOne; // p*120*1*0.8 = 57.6
        double expected = base * p; // multiple на survival p => 57.6 * 0.6 = 34.56

        double actual = policy.score(m, c);
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void one_life_can_heal_uses_survival_and_gold_penalty_with_floor() {
        double p = 0.5;
        int reward = 100;
        int expires = 0; // urgency = 1 + 0.5 = 1.5
        double lifePenaltyAtOne = 0.8;
        int potionCost = 100;
        double q = 0.7;
        double gamma = 0.1;

        var policy = policy(p, lifePenaltyAtOne, potionCost, q, gamma);
        var m = msg("gamble", reward, expires);
        var c = sc(1, 150,5); // gold >= potionCost → can heal

        double urgency = 1.0 + Math.max(0, 5 - expires) * 0.1; // 1.5
        double base = p * reward * urgency * lifePenaltyAtOne;  // 0.5*100*1.5*0.8 = 60.0

        double survival = p + (1 - p) * q;                      // 0.5 + 0.5*0.7 = 0.85
        double expectedGoldSpend = (1 - p) * potionCost;        // 0.5 * 100 = 50
        double goldPenalty = expectedGoldSpend * gamma;         // 50 * 0.1 = 5
        double expected = Math.max(0.0, base * survival - goldPenalty); // 60*0.85 - 5 = 46 - 5 = 41

        double actual = policy.score(m, c);
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void gold_penalty_floor_at_zero() {
        double p = 0.1;
        int reward = 60;
        int expires = 0;             // urgency=1.5
        double lifePenaltyAtOne = 0.8;
        int potionCost = 500;        // expensive potion
        double q = 0.7;
        double gamma = 0.2;          // convert to gold affected

        var policy = policy(p, lifePenaltyAtOne, potionCost, q, gamma);
        var m = msg("impossible", reward, expires);
        var c = sc(1, 1000, 3);

        double urgency = 1.0 + Math.max(0, 5 - expires) * 0.1; // 1.5
        double base = p * reward * urgency * lifePenaltyAtOne;  // 0.1*60*1.5*0.8 = 7.2
        double survival = p + (1 - p) * q;                      // 0.1 + 0.9*0.7 = 0.73
        double goldPenalty = (1 - p) * potionCost * gamma;      // 0.9*500*0.2 = 90
        double raw = base * survival - goldPenalty;             // 7.2*0.73 - 90 < 0
        double expected = Math.max(0.0, raw);                   // floor at 0

        double actual = policy.score(m, c);
        assertEquals(expected, actual, 1e-9);
    }
}
