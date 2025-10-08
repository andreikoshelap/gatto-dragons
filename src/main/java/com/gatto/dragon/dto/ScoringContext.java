package com.gatto.dragon.dto;

import org.springframework.lang.Nullable;

public record ScoringContext(
        int lives,
        @Nullable
        Reputation reputation) {
}
