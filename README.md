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

### Switching in `application.yml`
```yaml
dragon:
  scoring:
    policy: ev   # ev 
```

At startup the app logs the active strategy:
```
Selected scoring policy: <beanId> (cfg=ev)
```

## 2) Probability Labels → Numbers (with Calibration)
In the game, ads come with discrete probability labels such as:
```
sure thing, piece of cake, walk in the park, quite likely, hmmm....,
gamble, risky, rather detrimental, playing with fire, suicide mission, impossible

```
We map these labels to initial priors p -> [0,1] and calibrate them during play with Bayesian smoothing (alpha = 10):

```
p_calibrated = (successes + α · prior) / (attempts + α)
```
After each game we log the current mapping (calibrator.dump(gameId)).
Here is an example:
```
 === Probability mapping after game SUvrcavL ===
2025-10-12T20:43:46.799+03:00  label                          | prior | successes |  attempts | empirical |   calibr.
2025-10-12T20:43:46.800+03:00  --------------------+-------+-----------+-----------+-----------+-----------
2025-10-12T20:43:46.800+03:00  impossible                     | 0,01  |         0 |        42 |     0,000 | 0,002
2025-10-12T20:43:46.800+03:00  suicide mission                | 0,05  |         0 |        32 |     0,000 | 0,012
2025-10-12T20:43:46.801+03:00  playing with fire              | 0,10  |         3 |         8 |     0,375 | 0,222
2025-10-12T20:43:46.801+03:00  rather detrimental             | 0,20  |         3 |         6 |     0,500 | 0,313
2025-10-12T20:43:46.802+03:00  risky                          | 0,40  |         2 |         8 |     0,250 | 0,333
2025-10-12T20:43:46.802+03:00  r2ftymxl                       | 0,50  |         0 |         3 |     0,000 | 0,385
2025-10-12T20:43:46.803+03:00  u3vpy2lkzsbtaxnzaw9u           | 0,50  |         0 |         2 |     0,000 | 0,417
2025-10-12T20:43:46.803+03:00  sw1wb3nzawjszq==               | 0,50  |         0 |         1 |     0,000 | 0,455
2025-10-12T20:43:46.803+03:00  umlza3k=                       | 0,50  |         2 |         3 |     0,667 | 0,538
2025-10-12T20:43:46.803+03:00  sg1tbs4uli4=                   | 0,50  |         1 |         1 |     1,000 | 0,545
2025-10-12T20:43:46.803+03:00  v2fsaybpbib0agugcgfyaw==       | 0,50  |         1 |         1 |     1,000 | 0,545
2025-10-12T20:43:46.803+03:00  quite likely                   | 0,70  |         4 |        10 |     0,400 | 0,550
2025-10-12T20:43:46.803+03:00  hmmm....                       | 0,60  |         6 |        11 |     0,545 | 0,571
2025-10-12T20:43:46.803+03:00  ugxhewluzyb3axroigzpcmu=       | 0,50  |         5 |         7 |     0,714 | 0,588
2025-10-12T20:43:46.804+03:00  gamble                         | 0,50  |         6 |         8 |     0,750 | 0,611
2025-10-12T20:43:46.804+03:00  walk in the park               | 0,80  |        13 |        15 |     0,867 | 0,840
2025-10-12T20:43:46.804+03:00  piece of cake                  | 0,90  |        20 |        21 |     0,952 | 0,935
2025-10-12T20:43:46.804+03:00  sure thing                     | 0,95  |         5 |         5 |     1,000 | 0,967
```

## 3) Baseline: Expected Value (EV)

**Formula (used in code):**
```
EV = p × reward × (1 + max(0, 5 - expiresIn) × 0.1) × lifePenalty
where lifePenalty = (lives ≤ 1 ? 0.8 : 1.0)
```

- The **core** `p × reward` is the standard *expected value* of a risky payoff. It is the probability-weighted average outcome. Popular explanations: Investopedia; Corporate Finance Institute (CFI).
- The **urgency multiplier** `(1 + max(0, 5 - expiresIn) × 0.1)` is an engineering heuristic to favor soon-expiring ads; it’s inspired by time-discounting models that reward sooner outcomes more than later ones (see p.3). The specific constants (5, 0.1) are empirical.
- The **life penalty** (0.8 on one life) is a simple risk control reflecting higher aversion to failure when the “bankroll” (lives) is low; see Expected Utility motivation in p.2.

## 4) Healing (When and What Changes)
We heal only when lives == 1 — a simple, transparent heuristic.
**Flow (PotionHealingPolicy):**
  - After solve, we apply the result via StateMapper.applySolve to get up-to-date lives/gold/turn (and preserve gameId).
  - If lives > 1 or the game is over, do nothing.
  - Otherwise, fetch shop(gameId), pick the cheapest item whose name contains pot (case-insensitive).
  - If it’s affordable, call purchase(gameId, itemId) and then StateMapper.applyPurchase.

## 5) Encrypted Ad IDs
Some Message.adId values are base64-encoded (sometimes URL-safe).
MessageIdNormalizer does this:

  - If encrypted == false → return the trimmed id (URI encoding happens later).
  - If encrypted == true and the string looks like base64 → try to decode.
  - Use the decoded value only if it matches the typical 8-char token [A-Za-z0-9]{8}; otherwise keep the raw id.

## 6) References

- **Expected Value (EV)** — overview & formulae:
  - Investopedia — Expected Value: https://www.investopedia.com/terms/e/expected-value.asp
  - Corporate Finance Institute — Expected Value: https://corporatefinanceinstitute.com/resources/data-science/expected-value/

---
