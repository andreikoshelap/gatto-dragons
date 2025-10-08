package com.gatto.dragon.runner;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameRunner {
    private final GameClient api;

    private static double probabilityMap(String prob) {
        if (prob == null) return 0.5;
        return switch (prob.toLowerCase()) {
            case "sure thing" -> 0.95;
            case "piece of cake" -> 0.90;
            case "walk in the park" -> 0.80;
            case "quite likely" -> 0.70;
            case "hmm...." -> 0.60;
            case "risky" -> 0.40;
            case "gamble" -> 0.20;
            default -> 0.50;
        };
    }

    private static double score(Message m, int lives) {
        double prob = probabilityMap(m.probability());
        double urgency = 1.0 + Math.max(0, 5 - m.expiresIn()) * 0.1;
        double lifePenalty = lives <= 1 ? 0.8 : 1.0;
        return prob * m.reward() * urgency * lifePenalty;
    }

    public GameStart playOne() {
        GameStart game = api.start();
        log.debug(" gameId='{}'", game.gameId());
        while (game.lives() > 0) {
            var msgs = api.messages(game.gameId());
            if (msgs == null || msgs.isEmpty()) break;

            GameStart finalState = game;
            var best = msgs.stream()
                    .max(Comparator.comparingDouble(m -> score(m, finalState.lives())))
                    .orElseThrow();

            String adId = normalizeAdId(best);
            log.debug(" messId='{}' enc={}", adId, best.encrypted());

            var res = api.solve(game.gameId(), adId);

            if (res == null) break; // 410/404 -> game over
            if (res.lives() <= 0) {
                game = applySolve(game, res);
                break;
            }
            if (!res.success() || res.lives() == 1) {
                game = getBetter(game, res);
            } else {
                game = applySolve(game, res);
            }
        }
        return game;
    }

    private GameStart getBetter(GameStart prev, SolveResult res) {
        GameStart afterSolve = applySolve(prev, res);

        //Game over = stop
        if (afterSolve.lives() <= 0) {
            return afterSolve;
        }

        // no need to heal
        if (afterSolve.lives() > 1) {
            return afterSolve;
        }

        // try to sell potion
        var items = api.shop(prev.gameId());
        if (items == null || items.isEmpty()) {
            return afterSolve;
        }

        var potion = items.stream()
                .filter(i -> i.name() != null && i.name().toLowerCase().contains("pot"))
                .min(Comparator.comparingInt(ShopItem::cost))
                .orElse(null);

        if (potion == null || potion.cost() > afterSolve.gold()) {
            return afterSolve;
        }

        var pr = api.purchase(prev.gameId(), potion.id());
        if (pr == null || !pr.shoppingSuccess()) {
            return afterSolve;
        }

        return applyPurchase(afterSolve, pr);
    }



    private static GameStart applySolve(GameStart prev, SolveResult r) {

        if (prev == null) {
            throw new IllegalStateException("toState called with null prev; you must call start() and carry gameId forward");
        }

        int high = Math.max(prev.highScore(), r.highScore());
        return new GameStart(
                prev.gameId(),
                r.lives(),
                r.gold(),
                prev.level(),
                r.score(),
                high,
                r.turn()
        );
    }

    private static GameStart applyPurchase(GameStart prevOrAfterSolve, PurchaseResult p) {
        Objects.requireNonNull(prevOrAfterSolve, "state must not be null");
        int level = p.level() != null ? p.level() : prevOrAfterSolve.level();
        int high  = prevOrAfterSolve.highScore();
        return new GameStart(
                prevOrAfterSolve.gameId(),
                p.lives() != 0 ? p.lives() : prevOrAfterSolve.lives(),
                p.gold()  != 0 ? p.gold()  : prevOrAfterSolve.gold(),
                level,
                prevOrAfterSolve.score(),
                high,
                p.turn()  != 0 ? p.turn()  : prevOrAfterSolve.turn()
        );
    }

    // ---- Helpers: normalize adId when message is marked as encrypted ----
    private static String normalizeAdId(Message m) {
        // Trim raw adId; keep empty string if null
        String ad = m.adId() == null ? "" : m.adId().trim();

        // If the message is flagged as encrypted and adId looks like base64,
        // try to decode and use the decoded token (typical id is 8 alphanum chars)
        if (m.encrypted() && looksLikeBase64(ad)) {
            try {
                byte[] raw = java.util.Base64.getDecoder().decode(ad);
                String decoded = new String(raw, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (decoded.matches("^[A-Za-z0-9]{8}$")) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignore) {
                // Not actually base64 — fall back to the original adId
            }
        }
        return ad;
    }

    private static boolean looksLikeBase64(String s) {
        // Quick heuristic: length is a multiple of 4, allowed base64 chars, up to two '=' paddings
        return s.length() % 4 == 0 && s.matches("^[A-Za-z0-9+/_-]+={0,2}$");
    }

}
