package com.gatto.dragon.runner;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.Message;
import com.gatto.dragon.dto.ScoringContext;
import com.gatto.dragon.logic.HealingPolicy;
import com.gatto.dragon.logic.StateMapper;
import com.gatto.dragon.strategy.ProbabilityCalibrator;
import com.gatto.dragon.strategy.ReputationService;
import com.gatto.dragon.strategy.ScoringPolicy;
import com.gatto.dragon.util.MessageIdNormalizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameRunner {

    private final GameClient api;
    @Qualifier("scoringPolicy")
    private final ScoringPolicy scoringPolicy;
    private final HealingPolicy healingPolicy;
    private final ProbabilityCalibrator calibrator;
    private final MessageIdNormalizer messageIdNormalizer;
    private final StateMapper stateMapper;
    private final ReputationService reputationService;

    @PostConstruct
    void logPolicy() {
        log.info("Using scoring policy bean: {}", scoringPolicy.getClass().getSimpleName());
    }

    public Mono<Game> playOne() {
        return api.start()
                .flatMap(this::loop)
                .doOnSuccess(g -> calibrator.dump(g.gameId()));
    }

    private Mono<Game> loop(Game game) {
        if (game.lives() <= 0) {
            return Mono.just(game);
        }

        return api.messages(game.gameId())
                .flatMap(msgs -> {
                    if (msgs == null) {
                        return Mono.just(game);
                    }

                    ScoringContext ctx = new ScoringContext(
                            game.lives(),
                            game.gold(),
                            game.turn(),
                            null,
                            null
                    );

                    Message best = pickBest(msgs, ctx);
                    String adId = messageIdNormalizer.normalizeAdId(best.adId(), best.encrypted());

                    return api.solve(game.gameId(), adId)
                            .flatMap(res -> {
                                if (res == null) {
                                    return Mono.just(game);
                                }

                                safeRecordOutcome(best.probability(), res.success());

                                if (res.lives() <= 0) {
                                    Game after = stateMapper.applySolve(game, res);
                                    return Mono.just(after);
                                }

                                if (!res.success() || res.lives() == 1) {
                                    return healingPolicy.heal(game, res)
                                            .flatMap(next -> {
                                                if (next.lives() > 0) return loop(next);
                                                return Mono.just(next);
                                            });
                                } else {
                                    Game next = stateMapper.applySolve(game, res);
                                    if (next.lives() > 0) {
                                        return loop(next);
                                    }
                                    return Mono.just(next);
                                }
                            })
                            .onErrorResume(ex -> {
                                log.debug("solve failed: {} — ending game {}", ex.toString(), game.gameId());
                                return Mono.just(game);
                            });
                })
                .onErrorResume(ex -> {
                    log.debug("messages failed: {} — ending game {}", ex.toString(), game.gameId());
                    return Mono.just(game);
                });
    }

    private Message pickBest(List<Message> msgs, ScoringContext ctx) {
        return msgs.stream()
                .max(Comparator.comparingDouble(m -> scoringPolicy.score(m, ctx)))
                .orElseThrow();
    }

    private void safeRecordOutcome(String label, Boolean success) {
        try {
            calibrator.recordOutcome(label, Boolean.TRUE.equals(success));
        } catch (Throwable t) {
            log.warn("calibrator.recordOutcome failed: {}", t.toString());
        }
    }
}
