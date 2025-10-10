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

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applySolve(..))")
    public void applySolvePc() {}

    @Pointcut("execution(* com.gatto.dragon.logic.StateMapper.applyPurchase(..))")
    public void applyPurchasePc() {}


    @Around("startCall()")
    public Object aroundStart(ProceedingJoinPoint pjp) throws Throwable {
        Object out = pjp.proceed();
        if (out instanceof reactor.core.publisher.Mono<?> mono) {
            return mono.doOnNext(g -> {
                com.gatto.dragon.dto.Game game = (com.gatto.dragon.dto.Game) g;
                log.debug("HTTP start game id={}", game.gameId());
            });
        }
        return out;
    }

    @Around("applySolvePc()")
    public Object aroundApplySolve(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        com.gatto.dragon.dto.Game before = (com.gatto.dragon.dto.Game) args[0];
        com.gatto.dragon.dto.SolveResult res = (com.gatto.dragon.dto.SolveResult) args[1];
        int l0 = before.lives(), g0 = before.gold(), s0 = before.score();
        Object out = pjp.proceed();
        com.gatto.dragon.dto.Game after = (com.gatto.dragon.dto.Game) out;
        log.debug("APPLY_SOLVE gid={} success={} | lives:{}->{}({}), gold:{}->{}({}), score:{}->{}({}), turn:{}",
                before.gameId(), res.success(),
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

    private static String delta(int d) { return (d >= 0 ? "+" : "") + d; }
}
