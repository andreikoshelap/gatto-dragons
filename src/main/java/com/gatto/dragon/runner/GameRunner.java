package com.gatto.dragon.runner;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.Game;
import com.gatto.dragon.logic.HealingPolicy;
import com.gatto.dragon.logic.StateMapper;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ScoringPolicy;
import com.gatto.dragon.util.MessageIdNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@Slf4j
public class GameRunner {

    private final GameClient api;
    private final ScoringPolicy scoringPolicy;
    private final HealingPolicy healingPolicy;
    private final ProbabilityCalibrator calibrator;
    private final MessageIdNormalizer messageIdNormalizer;
    private final StateMapper stateMapper;

    @Autowired
    public GameRunner(
            GameClient api,
            @Qualifier("expectedValueScoringPolicy") ScoringPolicy scoringPolicy,
            HealingPolicy healingPolicy,
            ProbabilityCalibrator calibrator,
            MessageIdNormalizer messageIdNormalizer,
            StateMapper stateMapper
    ) {
        this.api = api;
        this.scoringPolicy = scoringPolicy;
        this.healingPolicy = healingPolicy;
        this.calibrator = calibrator;
        this.messageIdNormalizer = messageIdNormalizer;
        this.stateMapper = stateMapper;
    }

    public Game playOne() {
        Game game = api.start();
        log.debug("gameId='{}'", game.gameId());

        while (game.lives() > 0) {
            var msgs = api.messages(game.gameId());
            if (msgs == null || msgs.isEmpty()) break;

//            var rep = reputationService.get(game.gameId(), game.turn());
            final int lives = game.lives();
            var best = msgs.stream()
                    .max(Comparator.comparingDouble(m -> scoringPolicy.score(m, lives)))
                    .orElseThrow();

            String adId = messageIdNormalizer.normalizeAdId(best.adId(), best.encrypted());

            var res = api.solve(game.gameId(), adId);
            if (res == null) break; // 400/404/410 -> invalid ad/game over

            calibrator.recordOutcome(best.probability(), res.success());

            if (res.lives() <= 0) {
                game = stateMapper.applySolve(game, res);
                break;
            }
            if (!res.success() || res.lives() == 1) {
                game = healingPolicy.heal(game, res);
            } else {
                game = stateMapper.applySolve(game, res);
            }
        }
        calibrator.dump(game.gameId());
        return game;
    }
}
