package com.gatto.dragon.logic;

import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.SolveResult;
import reactor.core.publisher.Mono;

/**
 * Strategy that decides whether and how to heal after a solve result.
 * Returns the next game state (possibly unchanged).
 * Reactive healing: decides if/what to buy and returns the next Game state.
 * */
public interface HealingPolicy {
    Mono<Game> heal(Game prev, SolveResult res);
}
