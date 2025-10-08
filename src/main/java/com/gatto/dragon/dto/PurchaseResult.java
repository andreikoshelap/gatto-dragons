package com.gatto.dragon.dto;

public record PurchaseResult(
        boolean shoppingSuccess,
        int gold,
        int lives,
        Integer  level,
        int turn) {
}
