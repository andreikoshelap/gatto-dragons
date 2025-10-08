package com.gatto.dragon.dto;

public record GameStart(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int highScore,
        int turn) {
}
