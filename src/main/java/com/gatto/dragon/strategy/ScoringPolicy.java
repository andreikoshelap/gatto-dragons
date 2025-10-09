package com.gatto.dragon.strategy;

import com.gatto.dragon.dto.Message;

/**
 * Strategy for scoring a message (ad) given current game context.
 */
public interface ScoringPolicy {
    double score(Message message, int lives);
}
