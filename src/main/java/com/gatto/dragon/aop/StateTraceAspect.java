package com.gatto.dragon.aop;

import com.gatto.dragon.dto.Game;
import com.gatto.dragon.dto.PurchaseResult;
import com.gatto.dragon.dto.SolveResult;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class StateTraceAspect {

    // --- pointcuts ---
    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applySolve(..))")
    void applySolvePc() {
    }

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applyPurchase(..))")
    void applyPurchasePc() {
    }

    @Pointcut("execution(* com.gatto.dragon.api.GameClient.start(..))")
    void startCallPc() {
    }

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.solve(..)) && args(adId)",
            argNames = "adId")
    void solveCallPc(String adId) {
    }

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.purchase(..)) && args(itemId)",
            argNames = "itemId")
    void purchaseCallPc(String itemId) {
    }

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.investigateReputation(..)) && args(gameId)",
            argNames = "gameId")
    void repCallPc(String gameId) {
    }

    @AfterReturning(pointcut = "startCallPc()", returning = "ret")
    public void afterStart(Object ret) {
        Game g = (Game) ret;
        log.debug("HTTP start game id='{}'", g.gameId());
    }

    // --- AROUND: applySolve ---
    @Around("applySolvePc()")
    public Object aroundApplySolve(ProceedingJoinPoint pjp) throws Throwable {
        var args = pjp.getArgs();
        var before = (Game) args[0];
        var res = (SolveResult) args[1];

        int l0 = before != null ? before.lives() : 0;
        int g0 = before != null ? before.gold() : 0;
        int s0 = before != null ? before.score() : 0;

        var out = (Game) pjp.proceed();

        log.debug("APPLY_SOLVE success={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn={}",
                safeBool(res.success()),
                l0, out.lives(), fmtDelta(out.lives() - l0),
                g0, out.gold(), fmtDelta(out.gold() - g0),
                s0, out.score(), fmtDelta(out.score() - s0),
                out.turn());

        return out;
    }

    // --- AROUND: applyPurchase ---
    @Around("applyPurchasePc()")
    public Object aroundApplyPurchase(ProceedingJoinPoint pjp) throws Throwable {
        var args = pjp.getArgs();
        var before = (Game) args[0];
        var pr = (PurchaseResult) args[1];

        var out = (Game) pjp.proceed();

        log.debug("APPLY_PURCHASE ok={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn={}",
                pr != null && safeBool(pr.shoppingSuccess()),
                before.lives(), out.lives(), fmtDelta(out.lives() - before.lives()),
                before.gold(), out.gold(), fmtDelta(out.gold() - before.gold()),
                before.score(), out.score(), fmtDelta(out.score() - before.score()),
                out.turn());

        return out;
    }

    // --- AFTER-RETURNING: solve/purchase/rep ---
    @AfterReturning(pointcut = "solveCallPc(adId)", returning = "ret", argNames = "adId,ret")
    public void afterSolveCall(String adId, Object ret) {
        var r = (SolveResult) ret;
        if (r == null) {
            log.warn("HTTP solve adId={} -> null", adId);
            return;
        }
        log.debug("HTTP solve adId={} -> success={} lives={} gold={} score={} turn={}",
                adId, safeBool(r.success()), r.lives(), r.gold(), r.score(), r.turn());
    }

    @AfterReturning(pointcut = "purchaseCallPc(itemId)", returning = "ret", argNames = "itemId,ret")
    public void afterPurchaseCall(String itemId, Object ret) {
        var pr = (PurchaseResult) ret;
        log.debug("HTTP purchase itemId={} -> ok={} lives={} gold={} turn={}",
                itemId, pr != null && safeBool(pr.shoppingSuccess()),
                nz(pr != null ? pr.lives() : null), nz(pr != null ? pr.gold() : null), nz(pr != null ? pr.turn() : null));
    }

    @AfterReturning(pointcut = "repCallPc(gameId)", returning = "ret", argNames = "gameId,ret")
    public void afterRepCall(String gameId, Object ret) {
        log.debug("HTTP investigateReputation gid={} -> {}", gameId, ret);
    }

    // helpers
    private static String fmtDelta(int d) {
        return (d >= 0 ? "+" : "") + d;
    }

    private static boolean safeBool(Boolean b) {
        return b != null && b;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}

