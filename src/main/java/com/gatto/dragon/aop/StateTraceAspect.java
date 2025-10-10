package com.gatto.dragon.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class StateTraceAspect {

    // ===== pointcuts =====
    @Pointcut("execution(* com.gatto.dragon.api.GameClient.start(..))")
    public void startCall() {}

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.solve(..)) && args(gameId, adId)", argNames = "gameId,adId")
    public void solveCall(String gameId, String adId) {}

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.purchase(..)) && args(gameId, itemId)", argNames = "gameId,itemId")
    public void purchaseCall(String gameId, String itemId) {}

    @Pointcut("execution(* com.gatto.dragon.api.GameClient.messages(..)) && args(gameId)")
    public void messagesCall(String gameId) {}

    @Pointcut("execution(* com.gatto.dragon.api.GameClient.shop(..)) && args(gameId)")
    public void shopCall(String gameId) {}

    // ===== mappers (синхронные) — можно оставить твои вокруг-советы =====
    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applySolve(..))")
    public void applySolvePc() {}

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applyPurchase(..))")
    public void applyPurchasePc() {}

    // ===== реактивные HTTP вызовы: оборачиваем Mono/Flux =====

    @Around("startCall()")
    public Object aroundStart(ProceedingJoinPoint pjp) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            return mono.doOnNext(g -> {
                com.gatto.dragon.dto.Game game = (com.gatto.dragon.dto.Game) g;
                log.debug("HTTP start gid={}", game.gameId());
            });
        }
        return out; // на случай синхронной реализации
    }

    @Around(value = "solveCall(gameId, adId)", argNames = "pjp,gameId,adId")
    public Object aroundSolve(ProceedingJoinPoint pjp, String gameId, String adId) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            return mono.doOnNext(r -> {
                com.gatto.dragon.dto.SolveResult res = (com.gatto.dragon.dto.SolveResult) r;
                if (res != null) {
                    log.debug("HTTP solve gid={} adId={} -> success={} lives={} gold={} score={} turn={}",
                            gameId, adId, Boolean.TRUE.equals(res.success()),
                            res.lives(), res.gold(), res.score(), res.turn());
                } else {
                    log.warn("HTTP solve gid={} adId={} -> null", gameId, adId);
                }
            });
        }
        return out;
    }

    @Around(value = "purchaseCall(gameId, itemId)", argNames = "pjp,gameId,itemId")
    public Object aroundPurchase(ProceedingJoinPoint pjp, String gameId, String itemId) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            return mono.doOnNext(r -> {
                com.gatto.dragon.dto.PurchaseResult pr = (com.gatto.dragon.dto.PurchaseResult) r;
                if (pr != null) {
                    log.debug("HTTP purchase gid={} itemId={} -> ok={} lives={} gold={} turn={}",
                            gameId, itemId, Boolean.TRUE.equals(pr.shoppingSuccess()),
                            nz(pr.lives()), nz(pr.gold()), nz(pr.turn()));
                } else {
                    log.warn("HTTP purchase gid={} itemId={} -> null", gameId, itemId);
                }
            });
        }
        return out;
    }

    @Around(value = "messagesCall(gameId)", argNames = "pjp,gameId")
    public Object aroundMessages(ProceedingJoinPoint pjp, String gameId) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            @SuppressWarnings("unchecked")
            reactor.core.publisher.Mono<java.util.List<com.gatto.dragon.dto.Message>> m =
                    (reactor.core.publisher.Mono<java.util.List<com.gatto.dragon.dto.Message>>) mono;

            return m.doOnNext(list -> log.debug("HTTP messages gid={} -> {} items", gameId,
                    (list == null ? 0 : list.size())));
        }
        return out;
    }

    @Around(value = "shopCall(gameId)", argNames = "pjp,gameId")
    public Object aroundShop(ProceedingJoinPoint pjp, String gameId) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            @SuppressWarnings("unchecked")
            reactor.core.publisher.Mono<java.util.List<com.gatto.dragon.dto.ShopItem>> m =
                    (reactor.core.publisher.Mono<java.util.List<com.gatto.dragon.dto.ShopItem>>) mono;

            return m.doOnNext(list -> log.debug("HTTP shop gid={} -> {} items", gameId,
                    (list == null ? 0 : list.size())));
        }
        return out;
    }

    // ===== мапперы (синхронные) — как у тебя было, оставлю коротко =====

    @Around("applySolvePc()")
    public Object aroundApplySolve(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        com.gatto.dragon.dto.Game before = (com.gatto.dragon.dto.Game) args[0];
        com.gatto.dragon.dto.SolveResult res = (com.gatto.dragon.dto.SolveResult) args[1];
        int l0 = before.lives(), g0 = before.gold(), s0 = before.score();
        Object out = pjp.proceed();
        com.gatto.dragon.dto.Game after = (com.gatto.dragon.dto.Game) out;
        log.debug("APPLY_SOLVE gid={} success={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn:{}",
                before.gameId(), Boolean.TRUE.equals(res.success()),
                l0, after.lives(), delta(after.lives()-l0),
                g0, after.gold(),  delta(after.gold()-g0),
                s0, after.score(), delta(after.score()-s0),
                after.turn());
        return out;
    }

    @Around("applyPurchasePc()")
    public Object aroundApplyPurchase(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        com.gatto.dragon.dto.Game before = (com.gatto.dragon.dto.Game) args[0];
        com.gatto.dragon.dto.PurchaseResult pr = (com.gatto.dragon.dto.PurchaseResult) args[1];
        int l0 = before.lives(), g0 = before.gold(), s0 = before.score();
        Object out = pjp.proceed();
        com.gatto.dragon.dto.Game after = (com.gatto.dragon.dto.Game) out;
        log.debug("APPLY_PURCHASE gid={} ok={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn:{}",
                before.gameId(), pr != null && Boolean.TRUE.equals(pr.shoppingSuccess()),
                l0, after.lives(), delta(after.lives()-l0),
                g0, after.gold(),  delta(after.gold()-g0),
                s0, after.score(), delta(after.score()-s0),
                after.turn());
        return out;
    }

    // ===== helpers =====
    private static String delta(int d) { return (d >= 0 ? "+" : "") + d; }
    private static int nz(Integer v) { return v == null ? 0 : v; }
}
