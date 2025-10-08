package com.gatto.dragon.runner;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameRunner {
    public static final String ALLOWED_SYMBOLS = "^[A-Za-z0-9+/_-]+={0,2}$";
    private static final Pattern VALID_AD = Pattern.compile("^[A-Za-z0-9]{8}$");
    private final GameClient api;


    static final Map<String, Double> PROBABILITY_MAP = Map.of(
            "sure thing", 0.95, "piece of cake", 0.90, "walk in the park", 0.80,
            "quite likely", 0.70, "hmm....", 0.60, "risky", 0.40, "gamble", 0.20
    );

    static final ConcurrentHashMap<String, double[]> STATS = new ConcurrentHashMap<>();
    // STATS[label] = [successes, attempts]

    static double calibratedProb(String label) {
        String key = label == null ? "" : label.toLowerCase();
        double oldValue = PROBABILITY_MAP.getOrDefault(key, 0.5);
        double[] s = STATS.get(key);
        if (s == null) return oldValue;
        double result = s[0], attempt = s[1];
        double alpha = 10.0; // smoothing;
        return (result + alpha * oldValue) / (attempt + alpha);
    }

    static void recordOutcome(String label, boolean success) {
        String k = label == null ? "" : label.toLowerCase();
        STATS.compute(k, (kk, v) -> {
            if (v == null) v = new double[2];
            if (success) v[0]++; v[1]++; return v;
        });
    }

    private static double score(Message m, int lives) {
        double prob = calibratedProb(m.probability());
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
            recordOutcome(best.probability(), res.success());
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

    private static String normalizeAdId(Message m) {
        // Always trim
        String raw = m.adId() == null ? "" : m.adId().trim();

        // If not encrypted — just return raw (URI-encoding on client)
        if (!m.encrypted()) return raw;

        // Heuristic: looks like base64?
        if (!looksLikeBase64(raw)) return raw;

        try {
            boolean urlSafe = raw.indexOf('-') >= 0 || raw.indexOf('_') >= 0;
            byte[] bytes = (urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(raw);
            String decoded = new String(bytes, StandardCharsets.UTF_8).trim();

            // Use decoded only if it's a typical 8-char alnum token
            if (VALID_AD.matcher(decoded).matches()) {
                return decoded;
            }
        } catch (IllegalArgumentException ignore) {
            // Not actually base64 — fall back to raw
        }
        return raw;
    }

    private static boolean looksLikeBase64(String s) {
        // multiple of 4, allowed chars, up to two '=' paddings
        return s.length() % 4 == 0 && s.matches(ALLOWED_SYMBOLS);
    }

}
