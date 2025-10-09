package com.gatto.dragon.logic.impl;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.*;
import com.gatto.dragon.logic.HealingPolicy;
import com.gatto.dragon.logic.StateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Heals when lives == 1 by buying the cheapest potion, if affordable.
 */
@Component
@RequiredArgsConstructor
public class PotionHealingPolicy implements HealingPolicy {

    private final GameClient api;
    private final StateMapper mapper;

    @Override
    public Game heal(Game previous, SolveResult res) {
        // Apply to solve first to get up-to-date lives/gold while keeping gameId
        Game afterSolve = mapper.applySolve(previous, res);

        // Game already over; nothing to do
        if (afterSolve.lives() <= 0) return afterSolve;

        // No need to heal if we have more than 1 life
        if (afterSolve.lives() > 1) return afterSolve;

        var items = api.shop(previous.gameId());
        if (items == null || items.isEmpty()) return afterSolve;

        var potion = items.stream()
                .filter(i -> i.name() != null && i.name().toLowerCase().contains("pot"))
                .min(Comparator.comparingInt(ShopItem::cost))
                .orElse(null);

        if (potion == null || potion.cost() > afterSolve.gold()) return afterSolve;

        PurchaseResult pr = api.purchase(previous.gameId(), potion.id());
        if (pr == null || (pr.shoppingSuccess() != null && !pr.shoppingSuccess())) return afterSolve;

        return mapper.applyPurchase(afterSolve, pr);
    }
}
