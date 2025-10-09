package com.gatto.dragon.logic;

import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.PurchaseResult;
import com.gatto.dragon.dto.SolveResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StateMapper {

    public Game applySolve(Game prev, SolveResult r) {
        Objects.requireNonNull(prev, "prev state must not be null");
        int high = Math.max(prev.highScore(), r.highScore());
        return new Game(
                prev.gameId(),
                r.lives(),
                r.gold(),
                prev.level(),     // SolveResult has no level
                r.score(),
                high,
                r.turn()
        );
    }

    // PurchaseResult does NOT contain highScore => keep it from current state
    public Game applyPurchase(Game state, PurchaseResult p) {
        Objects.requireNonNull(state, "state must not be null");
        if (p == null || (p.shoppingSuccess() != null && !p.shoppingSuccess())) return state;

        int lives = p.lives() != null ? p.lives() : state.lives();
        int gold  = p.gold()  != null ? p.gold()  : state.gold();
        int level = p.level() != null ? p.level() : state.level();
        int turn  = p.turn()  != null ? p.turn()  : state.turn();

        return new Game(
                state.gameId(),
                lives,
                gold,
                level,
                state.score(),
                state.highScore(),
                turn
        );
    }
}
