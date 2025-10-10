package com.gatto.dragon.logic;

import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.PurchaseResult;
import com.gatto.dragon.dto.SolveResult;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** Pure mapping from server results to our immutable Game state. */
@Component
public class StateMapper {

    public Game applySolve(Game prev, SolveResult r) {
        Objects.requireNonNull(prev, "prev must not be null");
        int high = Math.max(prev.highScore(), r.highScore());
        return new Game(
                prev.gameId(),
                r.lives(),
                r.gold(),
                prev.level(),
                r.score(),
                high,
                r.turn()
        );
    }

    public Game applyPurchase(Game prevOrAfterSolve, PurchaseResult p) {
        Objects.requireNonNull(prevOrAfterSolve, "state must not be null");
        if (p == null) return prevOrAfterSolve;

        int level = p.level() != null ? p.level() : prevOrAfterSolve.level();

        return new Game(
                prevOrAfterSolve.gameId(),
                p.lives() != 0 ? p.lives() : prevOrAfterSolve.lives(),
                p.gold()  != 0 ? p.gold()  : prevOrAfterSolve.gold(),
                level,
                prevOrAfterSolve.score(),
                prevOrAfterSolve.highScore(),
                p.turn()  != 0 ? p.turn()  : prevOrAfterSolve.turn()
        );
    }
}
