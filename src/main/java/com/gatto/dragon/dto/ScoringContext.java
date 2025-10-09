package com.gatto.dragon.dto;

import org.springframework.lang.Nullable;

/** Immutable context for scoring decisions */
public record ScoringContext(
        int lives,
        int gold,
        int turn,
        @Nullable
        Reputation reputation,
        Integer potionCostHint
) {}
