package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Expected-Value scoring with calibrated probability, urgency, and life penalty.
 */
@Component("expectedValueScoringPolicy")
@RequiredArgsConstructor
public class EvScoringPolicy implements ScoringPolicy {

    private final ProbabilityCalibrator calibrator;

    @Override
    public double score(Message m,  ScoringContext ctx) {
        double prob = calibrator.calibratedProb(m.probability());
        double urgency = 1.0 + Math.max(0, 5 - m.expiresIn()) * 0.1;
        double lifePenalty = ctx.lives() <= 1 ? 0.8 : 1.0;
        return prob * m.reward() * urgency * lifePenalty;
    }
}

