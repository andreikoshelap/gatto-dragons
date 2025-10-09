package com.gatto.dragon.strategy.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for EV-style scoring without Mockito.
 * Uses a fixed calibrator and real Message records.
 */
class EvScoringPolicyTest extends AbstractScoringTestBase {

    // Helper to build SUT with fixed calibrated probability
     private EvScoringPolicy policyWithProb(double calibratedProb) {
        return new EvScoringPolicy(new FixedCalibrator(calibratedProb));
    }

    @Test
    void score_usesCalibratedProbability_andNoUrgency_NoLifePenalty() {
        // prob=0.90, reward=100, expiresIn=5 -> urgency=1.0, lives=2 -> lifePenalty=1.0
        var policy = policyWithProb(0.90);
        var m = msg("sure thing", 100, 5);

        double actual = policy.score(m, sc(2, 300, 10));

        assertEquals(90.0, actual, 1e-9);
    }

    @Test
    void score_appliesUrgencyBoost_whenExpiresSoon() {
        // prob=0.5, reward=100, expiresIn=0 -> urgency=1 + (5-0)*0.1 = 1.5, lives=3 -> lifePenalty=1.0
        var policy = policyWithProb(0.50);
        var m = msg("hmm....", 100, 0);

        double actual = policy.score(m, sc(3, 300, 10));

        assertEquals(75.0, actual, 1e-9); // 0.5 * 100 * 1.5 * 1.0
    }

    @Test
    void score_appliesLifePenalty_whenOneLifeLeft() {
        // prob=0.8, reward=100, expiresIn=5 -> urgency=1.0; lives=1 -> lifePenalty=0.8
        var policy = policyWithProb(0.80);
        var m = msg("quite likely", 100, 5);

        double actual = policy.score(m, sc(1, 300, 10));

        assertEquals(64.0, actual, 1e-9); // 0.8 * 100 * 1.0 * 0.8
    }

    @Test
    void score_combined_case() {
        // prob=0.7, reward=60, expiresIn=2 -> urgency=1 + (5-2)*0.1 = 1.3; lives=1 -> 0.8
        var policy = policyWithProb(0.70);
        var m = msg("quite likely", 60, 2);

        double actual = policy.score(m, sc(1, 300, 10));

        // 0.70 * 60 * 1.3 * 0.8 = 43.68
        assertEquals(43.68, actual, 1e-9);
    }

    @Test
    void score_handlesNullProbabilityLabel() {
        // Calibrator returns fixed 0.5 regardless of label (even null)
        var policy = policyWithProb(0.50);
        var m = msg(null, 40, 10); // expiresIn >= 5 -> urgency = 1.0

        double actual = policy.score(m, sc(2, 100, 1));

        assertEquals(20.0, actual, 1e-9);
    }
}
