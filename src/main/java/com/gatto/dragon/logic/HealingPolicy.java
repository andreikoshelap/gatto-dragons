package com.gatto.dragon.logic;

import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.SolveResult;

/**
 * Strategy that decides whether and how to heal after a solve result.
 * Returns the next game state (possibly unchanged).
 */
public interface HealingPolicy {
    Game heal(Game previous, SolveResult result);
}
