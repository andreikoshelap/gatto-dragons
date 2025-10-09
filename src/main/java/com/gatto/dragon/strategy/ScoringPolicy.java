package com.gatto.dragon.strategy;

import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;

/**
 * Strategy for scoring a message (ad) given current game context.
 */
public interface ScoringPolicy {
    double score(Message message, ScoringContext ctx);
}
