package com.gatto.dragon.strategy.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for RiskAverseScoringPolicy without Mockito.
 * Uses a fixed calibrator and real Message records.
 */
class RiskAverseScoringPolicyTest extends AbstractScoringTestBase {

    private RiskAverseScoringPolicy policyWithProb(double p) {
        return new RiskAverseScoringPolicy(new FixedCalibrator(p));
    }

    @Test
    void score_highProb_noUrgency_twoLives() {
        // p=0.90, lives=2 => rho=1.5, pAdj = 0.9^1.5
        // reward=100 => rewardAdj = 100^0.85
        // expiresIn=5 => urgency=1.0 (no boost)
        // p>=0.4 => lowProbPenalty=1.0
        var policy = policyWithProb(0.90);
        var m = msg("sure thing", 100, 5);

        double pAdj = Math.pow(0.90, 1.5);
        double rewardAdj = Math.pow(100.0, 0.85);
        double urgency = 1.0;
        double lowProbPenalty = 1.0;
        double expected = pAdj * rewardAdj * urgency * lowProbPenalty;

        double actual = policy.score(m, sc(2, 300, 10));
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void score_urgencyCapped_whenExpiresSoon() {
        // p=0.50, lives=3 => rho=1.2
        // expiresIn=0 => miss=5 -> 0.15*5=0.75 but capped to 0.5 => urgency=1.5
        // reward=80 => rewardAdj = 80^0.85
        var policy = policyWithProb(0.50);
        var m = msg("hmm....", 80, 0);

        double pAdj = Math.pow(0.50, 1.2);
        double rewardAdj = Math.pow(80.0, 0.85);
        double urgency = 1.5; // capped
        double expected = pAdj * rewardAdj * urgency;

        double actual = policy.score(m, sc(3, 300, 10));
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void score_lowProbPenalty_strongerOnOneLife() {
        // p=0.30 (<0.4), lives=1 => k=1.2, deficit=0.1 => penalty = 1 - 1.2*0.1 = 0.88
        // expiresIn=5 => urgency=1.0
        // reward=60 => rewardAdj = 60^0.85
        var policy = policyWithProb(0.30);
        var m = msg("risky", 60, 5);

        double pAdj = Math.pow(0.30, 1.8);  // rho=1.8 for lives<=1
        double rewardAdj = Math.pow(60.0, 0.85);
        double lowProbPenalty = 0.88;
        double expected = pAdj * rewardAdj * 1.0 * lowProbPenalty;

        double actual = policy.score(m, sc(1, 300, 10));
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void score_lowProbPenalty_milderOnTwoLives() {
        // p=0.30, lives=2 => k=0.8, penalty = 1 - 0.8*0.1 = 0.92
        var policy = policyWithProb(0.30);
        var m = msg("risky", 60, 5);

        double pAdj = Math.pow(0.30, 1.5);  // rho=1.5 for 2 lives
        double rewardAdj = Math.pow(60.0, 0.85);
        double lowProbPenalty = 0.92;
        double expected = pAdj * rewardAdj * 1.0 * lowProbPenalty;

        double actual = policy.score(m, sc(2, 300, 10));
        assertEquals(expected, actual, 1e-9);
    }

    @Test
    void score_negativeReward_isClampedToZero() {
        // reward < 0 => Math.max(0, reward) => 0 => whole score becomes 0
        var policy = policyWithProb(0.75);
        var m = msg("quite likely", -10, 3);

        double actual = policy.score(m, sc(3, 300, 10));
        assertEquals(0.0, actual, 1e-12);
    }

    @Test
    void score_combined_case_oneLife_andUrgency() {
        // p=0.65, lives=1 => rho=1.8
        // expiresIn=2 => miss=3 => urgency=1 + 0.15*3 = 1.45 (no cap)
        // reward=100 => rewardAdj = 100^0.85
        // p>=0.4 => no lowProbPenalty
        var policy = policyWithProb(0.65);
        var m = msg("quite likely", 100, 2);

        double pAdj = Math.pow(0.65, 1.8);
        double rewardAdj = Math.pow(100.0, 0.85);
        double urgency = 1.45;
        double expected = pAdj * rewardAdj * urgency;

        double actual = policy.score(m, sc(1, 300, 10));
        assertEquals(expected, actual, 1e-9);
    }
}
