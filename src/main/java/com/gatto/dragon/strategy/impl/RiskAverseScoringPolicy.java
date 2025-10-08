package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Risk-averse scoring:
 * - uses calibrated probability
 * - raises probability to rho > 1 (penalizes uncertainty)
 * - applies diminishing returns to reward (beta < 1)
 * - caps urgency boost
 * - adds extra penalty for very low probabilities, stronger when lives are low
 */
@Component("riskAverseScoringPolicy")
@RequiredArgsConstructor
public class RiskAverseScoringPolicy implements ScoringPolicy {

    private final ProbabilityCalibrator calibrator;

    @Override
    public double score(Message m, ScoringContext ctx) {
        // 1) calibrated probability from outcomes
        double p = calibrator.calibratedProb(m.probability());

        // 2) risk aversion exponent: stronger when lives are low
        //    rho > 1 shrinks probabilities < 1 (penalizes risk)
        double rho = (ctx.lives() <= 1) ? 1.8 : (ctx.lives() == 2 ? 1.5 : 1.2);
        double pAdj = Math.pow(p, rho);

        // 3) diminishing returns on reward to avoid being lured by huge but risky payouts
        //    beta in (0,1]; smaller => stronger diminishing
        double beta = 0.85;
        double rewardAdj = Math.pow(Math.max(0, m.reward()), beta);

        // 4) urgency boost for soon-to-expire ads, capped to avoid overpowering risk aversion
        int miss = Math.max(0, 5 - m.expiresIn());
        double urgency = 1.0 + Math.min(0.5, 0.15 * miss); // cap at +50%

        // 5) extra penalty for very low probabilities, stronger when lives are low
        //    linearly penalize when p < 0.4; never drop below 0.5x
        double lowProbPenalty = 1.0;
        if (p < 0.4) {
            double deficit = 0.4 - p; // [0..0.4]
            double k = (ctx.lives() <= 1) ? 1.2 : (ctx.lives() == 2 ? 0.8 : 0.5);
            lowProbPenalty = Math.max(0.5, 1.0 - k * deficit);
        }

        return pAdj * rewardAdj * urgency * lowProbPenalty;
    }
}
