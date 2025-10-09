package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.strategy.ProbabilityCalibrator;

/** Shared helpers for scoring policy tests. */
abstract class AbstractScoringTestBase {

    /** Calibrator stub that always returns a fixed probability. */
    static class FixedCalibrator extends ProbabilityCalibrator {
        private final double p;
        FixedCalibrator(double p) { this.p = p; }
        @Override public double calibratedProb(String label) { return p; }
    }

    /**
     * Build a real Message record for tests.
     * Adjust args if your Message signature differs.
     * Message(String adId, String message, int reward, int expiresIn, Boolean encrypted, String probability)
     */
    protected Message msg(String probabilityLabel, int reward, int expiresIn) {
        return new Message("AD123456", "text", reward, expiresIn, false, probabilityLabel);
    }
}
