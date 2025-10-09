package com.gatto.dragon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Game(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int highScore,
        int turn) {
}
