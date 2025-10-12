# Dragons of Mugloar — Scoring Policies


**Stack**
- Java 21
- Spring Boot
- Lombok
- Jackson
- Gradle
- REST layer for inspecting/controlling a running game
- JUnit 5
- Testcontainers

**Architecture (high level)**
- **GameClient** — thin wrapper over the Dragons of Mugloar HTTP API:
  - `start()`, `messages(gameId)`, `solve(gameId, adId)`, `shop(gameId)`, `purchase(gameId, itemId)`
- **Strategy** — scoring/selection interface to choose the next action (see `ScoringPolicy` and `ScoringContext`).
- **GameRunner** — turn loop:
  1. fetch messages
  2. choose the best ad via strategy
  3. `solve`
  4. if needed, go to `shop` (healing policy) and apply purchase
  5. repeat until the game ends
- **CommandLineRunner** — demo runner: plays N games (default 2) and prints results.

**Suggested package layout**
```
com.gatto.dragon.api        // GameClient (HTTP)
com.gatto.dragon.aop        // Aspects for loging/monitoring
com.gatto.dragon.dto        // Game, Message, SolveResult, PurchaseResult
com.gatto.dragon.runner     // GameRunner
com.gatto.dragon.logic      // StateMapper, HealingPolicy (+ impls)
com.gatto.dragon.strategy   // ScoringPolicy, ScoringContext, ProbabilityCalibrator (+ impls)
com.gatto.dragon.util       // MessageIdNormalizer, helpers
com.gatto.dragon.config     // Strategy router, urgency config
```
**Strategy router**
## 1) Strategies & How to Switch Them

You can choose one of several **scoring strategies**. Selection is done in `application.yml` (or via env var).

### Available strategies
- **ev** *(default)*— *Expected Value*. Simple, greedy on EV with a small urgency bump and life penalty at 1 life.
- **riskAverse** — Penalizes uncertainty and uses diminishing returns on reward. More conservative when lives are low.
- **bankrollAware**  — EV adjusted by survival and expected healing costs when lives are low. Helps avoid sudden game-over and manages potion cost vs. gold.

### Switching in `application.yml`
```yaml
dragon:
  scoring:
    policy: ev   # ev 
```

At startup the app logs the active strategy:
```
Selected scoring policy: <beanId> (cfg=bankrollAware)
```

> Under the hood we use a small router bean (`ScoringPolicyConfig`) that reads `dragon.scoring.policy` and provides the matching `ScoringPolicy` implementation to the runner.

## 2) Baseline: Expected Value (EV)

**Formula (used in code):**
```
EV = p × reward × (1 + max(0, 5 - expiresIn) × 0.1) × lifePenalty
where lifePenalty = (lives ≤ 1 ? 0.8 : 1.0)
```

- The **core** `p × reward` is the standard *expected value* of a risky payoff. It is the probability-weighted average outcome. Popular explanations: Investopedia; Corporate Finance Institute (CFI).
- The **urgency multiplier** `(1 + max(0, 5 - expiresIn) × 0.1)` is an engineering heuristic to favor soon-expiring ads; it’s inspired by time-discounting models that reward sooner outcomes more than later ones (see p.3). The specific constants (5, 0.1) are empirical.
- The **life penalty** (0.8 on one life) is a simple risk control reflecting higher aversion to failure when the “bankroll” (lives) is low; see Expected Utility motivation in p.2.


## 3) References

- **Expected Value (EV)** — overview & formulae:
  - Investopedia — Expected Value: https://www.investopedia.com/terms/e/expected-value.asp
  - Corporate Finance Institute — Expected Value: https://corporatefinanceinstitute.com/resources/data-science/expected-value/

---
