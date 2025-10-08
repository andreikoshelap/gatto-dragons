package com.gatto.dragon.strategy.impl;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("repAwareScoringPolicy")
@RequiredArgsConstructor
public class RepAwareScoringPolicy implements ScoringPolicy {
    private final ProbabilityCalibrator calibrator;

    @Override
    public double score(Message m, ScoringContext ctx) {
        double base = new EvScoringPolicy(calibrator).score(m, ctx);
        var rep = ctx.reputation();
        if (rep == null) return base;

        Domain d = guessDomain(m);
        int v = switch (d) {
            case PEOPLE -> rep.people();
            case STATE -> rep.state();
            case UNDERWORLD -> rep.underworld();
            default -> 0;
        };
        double bias = 1.0 + 0.05 * Math.max(-1, Math.min(1, v / 100.0));
        return base * bias;
    }

    enum Domain { PEOPLE, STATE, UNDERWORLD, NONE }
    private Domain guessDomain(Message m) {
        var t = (m.message() == null ? "" : m.message().toLowerCase());
        if (t.contains("citizen") || t.contains("villager") || t.contains("family")) return Domain.PEOPLE;
        if (t.contains("minister") || t.contains("police") || t.contains("tax"))   return Domain.STATE;
        if (t.contains("smuggler") || t.contains("gang") || t.contains("underworld")) return Domain.UNDERWORLD;
        return Domain.NONE;
    }
}
