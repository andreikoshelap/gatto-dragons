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

    // ======= POINTCUTS for state mutations (mapper) =======

    // adjust fully-qualified class if your mapper is elsewhere

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applySolve(..))")
    public void applySolvePc() {}

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applyPurchase(..))")
    public void applyPurchasePc() {}

    // ======= POINTCUTS for HTTP client calls (GameClient) =======

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.start(..))")
    public void applyStart() {}

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.solve(..)) && args(gameId, adId)", argNames = "gameId,adId")
    public void solveCallPc(String gameId, String adId) {}

    @Pointcut(value = "execution(* com.gatto.dragon.api.GameClient.purchase(..)) && args(gameId, itemId)", argNames = "gameId,itemId")
    public void purchaseCallPc(String gameId, String itemId) {}

    @Pointcut("execution(* com.gatto.dragon.api.GameClient.investigateReputation(..)) && args(gameId)")
    public void repCallPc(String gameId) {}

    // ======= AROUND: applySolve — delta log =======

    @Around("applySolvePc()")
    public Object aroundApplySolve(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Game before = (Game) args[0];
        SolveResult res  = (SolveResult) args[1];

        String gid = before != null ? before.gameId() : "?";
        int l0 = before != null ? before.lives() : 0;
        int g0 = before != null ? before.gold()  : 0;
        int s0 = before != null ? before.score() : 0;

        Object out = pjp.proceed();
        Game after = (Game) out;

        int dl = (after.lives() - l0);
        int dg = (after.gold()  - g0);
        int ds = (after.score() - s0);

        log.debug("APPLY_SOLVE gid={} success={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn:{})",
                gid,
                safeBool(res.success()),
                l0, after.lives(), fmtDelta(dl),
                g0, after.gold(),  fmtDelta(dg),
                s0, after.score(), fmtDelta(ds),
                after.turn());

        return out;
    }

    // ======= AROUND: applyPurchase — delta log =======

    @Around("applyPurchasePc()")
    public Object aroundApplyPurchase(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        Game before = (Game) args[0];
        PurchaseResult pr = (PurchaseResult) args[1];

        String gid = before.gameId();
        int l0 = before.lives();
        int g0 = before.gold();
        int s0 = before.score();

        Object out = pjp.proceed();
        Game after = (Game) out;

        int dl = (after.lives() - l0);
        int dg = (after.gold()  - g0);
        int ds = (after.score() - s0);

        log.debug("APPLY_PURCHASE gid={} ok={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn:{})",
                gid,
                pr != null && safeBool(pr.shoppingSuccess()),
                l0, after.lives(), fmtDelta(dl),
                g0, after.gold(),  fmtDelta(dg),
                s0, after.score(), fmtDelta(ds),
                after.turn());

        return out;
    }

    // ======= AFTER-RETURNING: HTTP calls =======


    @AfterReturning(argNames = "applyStart()", returning = "ret")
    public void aroundApplyStart(Object ret) {
        Game g = (Game) ret;
        log.debug("HTTP start gid={} )", g.gameId());
    }

    @AfterReturning(pointcut = "solveCallPc(gameId, adId)", returning = "ret", argNames = "gameId,adId,ret")
    public void afterSolveCall(String gameId, String adId, Object ret) {
        SolveResult r = (SolveResult) ret;
        if (r == null) {
            log.warn("HTTP solve gid={} adId={} -> null (HTTP error mapped?)", gameId, adId);
            return;
        }
        log.debug("HTTP solve gid={} adId={} -> success={} lives={} gold={} score={} turn={}",
                gameId, adId, safeBool(r.success()), r.lives(), r.gold(), r.score(), r.turn());
    }

    @AfterReturning(pointcut = "purchaseCallPc(gameId, itemId)", returning = "ret", argNames = "gameId,itemId,ret")
    public void afterPurchaseCall(String gameId, String itemId, Object ret) {
        PurchaseResult pr = (PurchaseResult) ret;
        if (pr == null) {
            log.warn("HTTP purchase gid={} itemId={} -> null", gameId, itemId);
            return;
        }
        log.debug("HTTP purchase gid={} itemId={} -> ok={} lives={} gold={} turn={}",
                gameId, itemId, safeBool(pr.shoppingSuccess()), nz(pr.lives()), nz(pr.gold()), nz(pr.turn()));
    }

    @AfterReturning(pointcut = "repCallPc(gameId)", returning = "ret", argNames = "gameId,ret")
    public void afterRepCall(String gameId, Object ret) {
        if (ret == null) {
            log.debug("HTTP investigateReputation gid={} -> null", gameId);
            return;
        }
        // toString on record Reputation
        log.debug("HTTP investigateReputation gid={} -> {}", gameId, ret);
    }

    // ======= helpers =======

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

