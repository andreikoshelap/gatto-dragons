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
    policy: bankrollAware   # ev | riskAverse | bankrollAware
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

## 3) Risk Aversion & Utility

Real agents rarely maximize raw EV of money; they maximize **expected utility**, which captures diminishing marginal utility and risk aversion (e.g., preferring a sure small gain over a risky larger EV). The von Neumann–Morgenstern utility theorem formalizes when maximizing *expected utility* is rational. In practice, we implement risk aversion by transforming either the probability or the payoff (e.g., concave reward, probability exponents/penalties).

A related idea is the **Kelly criterion**, which maximizes long-run growth and explicitly accounts for *risk of ruin*; it’s often used to modulate bet size relative to bankroll. We don’t apply Kelly verbatim, but our bankroll-aware scoring borrows its spirit: scale aggressiveness down when capital/lives are low.

## 4) Risk-Averse Policy (RA)

**In code (idea):**
```
pAdj = p^ρ              // ρ > 1 penalizes uncertainty (stronger when lives low)
rewardAdj = reward^betta    // betta -> (0,1] imposes diminishing returns
urgency = 1 + min(0.5, 0.15 × max(0, 5 - expiresIn))
lowProbPenalty = piecewise penalty when p < 0.4 (stronger at 1 life)
score_RA = pAdj × rewardAdj × urgency × lowProbPenalty
```

This is a pragmatic *expected-utility-like* transform: concave payoff (`betta < 1`), probability dampening (`ρ > 1`), and extra penalties for very low probabilities, especially on one life. Constants (`ρ, β`, caps) are tuned to gameplay.

## 5) Bankroll-Aware Policy (BA)

At one life, failure often ends the game—unless you can buy a potion. We therefore adjust EV by a *survival factor* and the *expected healing cost*:

**Excel version in the shared workbook:**
[Excel file](./bankroll_aware_with_formulas_RA.xlsx)
```
EV = p * reward * (1 + MAX(0, 5 - expires) * 0.1) * IF(lives<=1, life_penalty_at_one, 1)

BA_gold=i =
  IF(lives>1, EV,
     IF(gold_i < potion_cost,
        EV * p,
        MAX(0,
            EV * (p + (1 - p) * q_survive_on_fail)
              - (1 - p) * potion_cost * gamma
        )
     )
  )
```
- If you **cannot** heal (gold < potion price): only success continues the run ⇒ multiply by `p`.
- If you **can** heal: assume a fraction `q` of failures can be recovered via potion; subtract expected potion cost converted to “score units” by `gamma`.
This scheme is conceptually related to bankroll-sensitive decision rules (e.g., Kelly-style thinking), adapted to this game’s mechanics.

## 6) References

- **Expected Value (EV)** — overview & formulae:
  - Investopedia — Expected Value: https://www.investopedia.com/terms/e/expected-value.asp
  - Corporate Finance Institute — Expected Value: https://corporatefinanceinstitute.com/resources/data-science/expected-value/

---
