package com.gatto.dragon.dto;

public record Game(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int highScore,
        int turn) {
}
