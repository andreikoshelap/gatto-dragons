package com.gatto.dragon.logic.impl;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.ShopItem;
import com.gatto.dragon.dto.SolveResult;
import com.gatto.dragon.logic.HealingPolicy;
import com.gatto.dragon.logic.StateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveHealingPolicy implements HealingPolicy {

    private final GameClient api;       // reactive GameClient
    private final StateMapper mapper;   // pure mapping, sync

    @Override
    public Mono<Game> heal(Game prev, SolveResult res) {
        if (res.lives() <= 0) {
            return Mono.just(mapper.applySolve(prev, res));
        }

        if (res.lives() > 1) {
            return Mono.just(mapper.applySolve(prev, res));
        }

        Game afterSolve = mapper.applySolve(prev, res);
        return api.shop(prev.gameId())
                .defaultIfEmpty(List.of())
                .map(this::pickCheapestPotion)
                .flatMap(potion -> {
                    if (potion == null) {
                        log.debug("No potion found in shop; return afterSolve");
                        return Mono.just(afterSolve);
                    }
                    if (potion.cost() > afterSolve.gold()) {
                        log.debug("Not enough gold for potion (cost={}, gold={})", potion.cost(), afterSolve.gold());
                        return Mono.just(afterSolve);
                    }
                    return api.purchase(prev.gameId(), potion.id())
                            .onErrorResume(ex -> {
                                log.debug("purchase failed: {}", ex.toString());
                                return Mono.empty();
                            })
                            .map(pr -> mapper.applyPurchase(afterSolve, pr))
                            .defaultIfEmpty(afterSolve);
                })
                .onErrorResume(ex -> {
                    log.debug("shop failed: {}", ex.toString());
                    return Mono.just(afterSolve);
                });
    }

    private ShopItem pickCheapestPotion(List<ShopItem> items) {
        return items.stream()
                .filter(i -> {
                    String n = i.name();
                    return n != null && n.toLowerCase().contains("pot");
                })
                .min(Comparator.comparingInt(ShopItem::cost))
                .orElse(null);
    }
}
