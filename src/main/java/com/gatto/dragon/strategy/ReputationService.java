package com.gatto.dragon.strategy;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.Reputation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReputationService {
    private final GameClient api;

    private Reputation last;    // cached value
    private int lastTurn = -1;

    /** Fetch at most once every N turns (defaults to 3) */
    public Reputation get(String gameId, int currentTurn) {
        int refreshEvery = 3;
        if (last == null || lastTurn < 0 || currentTurn - lastTurn >= refreshEvery) {
            last = api.investigateReputation(gameId);
            lastTurn = currentTurn;
        }
        return last;
    }

    public void reset() { last = null; lastTurn = -1; }
}
