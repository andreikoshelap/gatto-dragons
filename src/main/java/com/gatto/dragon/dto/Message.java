package com.gatto.dragon.dto;

public record Message(
        String adId,
        String message,
        int reward,
        int expiresIn,
        boolean encrypted,
        String probability) {
}
