package com.gatto.dragon.dto;

public record PurchaseResult(
        Boolean shoppingSuccess,
        Integer gold,
        Integer lives,
        Integer level,
        Integer turn) {
}
