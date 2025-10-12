package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Expected-Value scoring with calibrated probability, urgency, and life penalty.
 */
@Component("expectedValueScoringPolicy")
@RequiredArgsConstructor
public class EvScoringPolicy implements ScoringPolicy {

    private final ProbabilityCalibrator calibrator;
    @Value("${dragon.urgency.window}")
    private int urgencyWindow;
    @Value("${dragon.urgency.slope}")
    private double slope;

    @Override
    public double score(Message message, ScoringContext ctx) {
        // 1) calibrated probability from outcomes
        double prob = calibrator.calibratedProb(message.probability());
        // 2) risk aversion exponent: stronger when lives are low
        double lifePenalty = ctx.lives() <= 1 ? 0.8 : 1.0;
        // 3) urgency boost for soon-to-expire ads, capped to avoid overpowering risk aversion
        double urgency = 1.0 + Math.max(0, urgencyWindow - message.expiresIn()) * slope;

        return prob * message.reward() * urgency * lifePenalty;
    }
}

