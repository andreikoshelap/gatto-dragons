package com.gatto.dragon.logic.impl;

import com.gatto.dragon.api.GameClient;
import com.gatto.dragon.dto.*;
import com.gatto.dragon.logic.StateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for PotionHealingPolicy WITHOUT Mockito.
 * We use simple fakes for GameClient and StateMapper.
 */
class PotionHealingPolicyTest {

    private FakeGameClient api;
    private FakeStateMapper mapper;
    private PotionHealingPolicy policy;

    @BeforeEach
    void setUp() {
        this.api = new FakeGameClient();
        this.mapper = new FakeStateMapper();
        this.policy = new PotionHealingPolicy(api, mapper);
    }

    // ---------- helpers (records are assumed to exist in project) ----------

    private static Game game(String id, int lives, int gold, int score, int turn) {
        return new Game(id, lives, gold, 0, score, 0, turn);
    }

    private static SolveResult solve(int lives, int gold, int score, int high, int turn, Boolean success) {
        return new SolveResult(success, lives, gold, score, high, turn, "");
    }

    private static PurchaseResult purchase(Integer lives, Integer gold, Integer turn, Boolean ok, Integer level) {
        return new PurchaseResult(ok, gold, lives, turn, level);
    }

    private static ShopItem item(String id, String name, int cost) {
        return new ShopItem(id, name, cost);
    }

    // ---------- tests ----------

    @Test
    void whenGameOverAfterSolve_returnsAfterSolve_andDoesNotCallShop() {
        Game prev = game("G1", 2, 100, 0, 0);
        SolveResult res = solve(0, 100, 10, 10, 1, false);
        // Fake mapper will compute afterSolve from prev+res
        Game afterSolve = mapper.applySolve(prev, res);

        Game out = policy.heal(prev, res);

        assertThat(out).isEqualTo(afterSolve);
        assertThat(api.lastShopGameId).isNull();
        assertThat(api.lastPurchaseGameId).isNull();
    }

    @Test
    void whenLivesMoreThanOne_noHealing() {
        Game prev = game("G1", 3, 200, 0, 0);
        SolveResult res = solve(2, 200, 50, 50, 1, true);
        Game afterSolve = mapper.applySolve(prev, res);

        Game out = policy.heal(prev, res);

        assertThat(out).isEqualTo(afterSolve);
        assertThat(api.lastShopGameId).isNull();
        assertThat(api.lastPurchaseGameId).isNull();
    }

    @Test
    void whenOneLife_andShopEmpty_returnsAfterSolve() {
        Game prev = game("G1", 2, 150, 0, 0);
        SolveResult res = solve(1, 150, 60, 60, 1, false);

        api.nextShopItems = List.of(); // empty

        Game afterSolve = mapper.applySolve(prev, res);
        Game out = policy.heal(prev, res);

        assertThat(out).isEqualTo(afterSolve);
        assertThat(api.lastShopGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseGameId).isNull();
    }

    @Test
    void whenOneLife_andNoPotionInShop_returnsAfterSolve() {
        Game prev = game("G1", 2, 150, 0, 0);
        SolveResult res = solve(1, 150, 60, 60, 1, false);

        api.nextShopItems = List.of(
                item("A", "sword", 120),
                item("B", "shield", 80)
        );

        Game afterSolve = mapper.applySolve(prev, res);
        Game out = policy.heal(prev, res);

        assertThat(out).isEqualTo(afterSolve);
        assertThat(api.lastShopGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseGameId).isNull();
    }

    @Test
    void whenOneLife_andPotionTooExpensive_returnsAfterSolve() {
        Game prev = game("G1", 2, 50, 0, 0);
        SolveResult res = solve(1, 50, 30, 30, 1, false);

        api.nextShopItems = List.of(item("POT1", "Healing Pot", 120));

        Game afterSolve = mapper.applySolve(prev, res);
        Game out = policy.heal(prev, res);

        assertThat(out).isEqualTo(afterSolve);
        assertThat(api.lastShopGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseGameId).isNull();
    }

    @Test
    void whenOneLife_andAffordablePotion_andPurchaseOk_appliesPurchaseAndReturnsUpdatedState() {
        Game prev = game("G1", 2, 200, 0, 0);
        SolveResult res = solve(1, 200, 70, 70, 1, false);

        // shop: the cheapest pot (POTX, 90) will be chosen
        api.nextShopItems = List.of(
                item("A", "sword", 120),
                item("POTX", "magic POT of life", 90),
                item("B", "shield", 95)
        );
        // purchase successful: +life, gold decreased
        api.nextPurchaseResult = purchase(2, 110, 2, true, null);

        Game afterSolve = mapper.applySolve(prev, res);
        Game expected = mapper.applyPurchase(afterSolve, api.nextPurchaseResult);

        Game out = policy.heal(prev, res);

        assertThat(api.lastShopGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseItemId).isEqualTo("POTX");

        assertThat(out).isEqualTo(expected);
        assertThat(out.lives()).isEqualTo(2);
        assertThat(out.gold()).isEqualTo(110);
    }

    @Test
    void whenOneLife_andAffordablePotion_butPurchaseFails_returnsAfterSolve() {
        Game prev = game("G1", 2, 200, 0, 0);
        SolveResult res = solve(1, 200, 70, 70, 1, false);

        api.nextShopItems = List.of(item("POTX", "pot of life", 100));
        api.nextPurchaseResult = purchase(null, null, null, false, null); // fail

        Game afterSolve = mapper.applySolve(prev, res);
        Game out = policy.heal(prev, res);

        assertThat(api.lastShopGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseGameId).isEqualTo("G1");
        assertThat(api.lastPurchaseItemId).isEqualTo("POTX");
        assertThat(out).isEqualTo(afterSolve);
    }

    // ---------- simple fakes ----------

    /**
     * Simple in-memory fake for GameClient.
     * We override methods used by PotionHealingPolicy.
     */
    static class FakeGameClient extends GameClient {
        FakeGameClient() {
            super(RestClient.create()); // not used, all methods are overridden
        }

        List<ShopItem> nextShopItems = new ArrayList<>();
        PurchaseResult nextPurchaseResult;

        String lastShopGameId;
        String lastPurchaseGameId;
        String lastPurchaseItemId;

        @Override
        public List<ShopItem> shop(String gameId) {
            this.lastShopGameId = gameId;
            // return a snapshot of the list to avoid accidental mutations
            return nextShopItems == null ? null : List.copyOf(nextShopItems);
        }

        @Override
        public PurchaseResult purchase(String gameId, String itemIdRaw) {
            this.lastPurchaseGameId = gameId;
            this.lastPurchaseItemId = itemIdRaw == null ? "" : itemIdRaw.trim();
            return nextPurchaseResult;
        }
    }

    /**
     * Deterministic StateMapper, mirroring production logic.
     * (If your project uses a different mapping — adjust accordingly.)
     */
    static class FakeStateMapper extends StateMapper {

        @Override
        public Game applySolve(Game prev, SolveResult r) {
            Objects.requireNonNull(prev, "prev state is required");
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

        @Override
        public Game applyPurchase(Game afterSolve, PurchaseResult p) {
            Objects.requireNonNull(afterSolve, "afterSolve is required");
            int level = p.level() != null ? p.level() : afterSolve.level();
            return new Game(
                    afterSolve.gameId(),
                    p.lives() != null && p.lives() != 0 ? p.lives() : afterSolve.lives(),
                    p.gold() != null && p.gold() != 0 ? p.gold() : afterSolve.gold(),
                    level,
                    afterSolve.score(),
                    afterSolve.highScore(),
                    p.turn() != null && p.turn() != 0 ? p.turn() : afterSolve.turn()
            );
        }
    }
}
