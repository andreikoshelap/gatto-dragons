# Dragons of Mugloar — Scoring Policies

This README explains the scoring policies used by the bot and how they are motivated by decision theory and behavioral models. Some constants are empirical (tuned to this game), but the structure of the formulas is grounded in standard references.

## 1) Baseline: Expected Value (EV)

**Formula (used in code):**
```
EV = p × reward × (1 + max(0, 5 - expiresIn) × 0.1) × lifePenalty
where lifePenalty = (lives ≤ 1 ? 0.8 : 1.0)
```

- The **core** `p × reward` is the standard *expected value* of a risky payoff. It is the probability-weighted average outcome. Popular explanations: Investopedia; Corporate Finance Institute (CFI).
- The **urgency multiplier** `(1 + max(0, 5 - expiresIn) × 0.1)` is an engineering heuristic to favor soon-expiring ads; it’s inspired by time-discounting models that reward sooner outcomes more than later ones (see §3). The specific constants (5, 0.1) are empirical.
- The **life penalty** (0.8 on one life) is a simple risk control reflecting higher aversion to failure when the “bankroll” (lives) is low; see Expected Utility motivation in §2.

## 2) Risk Aversion & Utility

Real agents rarely maximize raw EV of money; they maximize **expected utility**, which captures diminishing marginal utility and risk aversion (e.g., preferring a sure small gain over a risky larger EV). The von Neumann–Morgenstern utility theorem formalizes when maximizing *expected utility* is rational. In practice, we implement risk aversion by transforming either the probability or the payoff (e.g., concave reward, probability exponents/penalties).

A related idea is the **Kelly criterion**, which maximizes long-run growth and explicitly accounts for *risk of ruin*; it’s often used to modulate bet size relative to bankroll. We don’t apply Kelly verbatim, but our bankroll-aware scoring borrows its spirit: scale aggressiveness down when capital/lives are low.

## 3) Time & Urgency

The *urgency* term is motivated by **time preference** and **discounting**: sooner rewards get more weight than later ones. Classical models use exponential discounting; behavioral evidence often supports **hyperbolic discounting** (present bias). We approximate this with a simple linear bump capped by design. If you prefer, you can replace our bump with an explicit discount factor `1 / (1 + k·delay)` (hyperbolic) or `exp(-r·delay)` (exponential).

## 4) Risk-Averse Policy (RA)

**In code (idea):**
```
pAdj = p^ρ              // ρ > 1 penalizes uncertainty (stronger when lives low)
rewardAdj = reward^betta    // betta ∈ (0,1] imposes diminishing returns
urgency = 1 + min(0.5, 0.15 × max(0, 5 - expiresIn))
lowProbPenalty = piecewise penalty when p < 0.4 (stronger at 1 life)
score_RA = pAdj × rewardAdj × urgency × lowProbPenalty
```

This is a pragmatic *expected-utility-like* transform: concave payoff (`β < 1`), probability dampening (`ρ > 1`), and extra penalties for very low probabilities, especially on one life. Constants (`ρ, β`, caps) are tuned to gameplay.

## 5) Bankroll-Aware Policy (BA)

At one life, failure often ends the game—unless you can buy a potion. We therefore adjust EV by a *survival factor* and the *expected healing cost*:

**Excel version in the shared workbook:**
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

## 6) Tuning & Alternatives

- Replace the linear **urgency** bump with explicit **discounting**:
  - **Exponential**: `discount = exp(-r·delay)`
  - **Hyperbolic**: `discount = 1/(1 + k·delay)`
  Calibrate `r`/`k` on logs by maximizing achieved score.
- Make risk aversion principled by using **concave utility** on rewards (`reward^β`) instead of ad-hoc lifePenalty, or combine both.
- Use **probability calibration** (we do) to map labels (“sure thing”, “risky”, …) to empirical success rates.

## 7) References & Further Reading

- **Expected Value (EV)** — overview & formulae:
  - Investopedia — Expected Value: https://www.investopedia.com/terms/e/expected-value.asp
  - Corporate Finance Institute — Expected Value: https://corporatefinanceinstitute.com/resources/data-science/expected-value/
- **Expected Utility Theory & VNM Theorem** — foundations of risk-averse choice:
  - Expected Utility Theory (Wikipedia): https://en.wikipedia.org/wiki/Expected_utility_hypothesis
  - von Neumann–Morgenstern utility theorem (Wikipedia): https://en.wikipedia.org/wiki/Von_Neumann%E2%80%93Morgenstern_utility_theorem
- **Kelly Criterion** — bankroll-aware growth-optimal betting:
  - J. L. Kelly (1956), A New Interpretation of Information Rate (Bell System Technical Journal): https://archive.org/details/bstj35-4-917
  - Kelly criterion (Wikipedia): https://en.wikipedia.org/wiki/Kelly_criterion
- **Time Preference & Discounting** — exponential vs hyperbolic (present bias):
  - Time preference (Wikipedia): https://en.wikipedia.org/wiki/Time_preference
  - Hyperbolic discounting (Wikipedia): https://en.wikipedia.org/wiki/Hyperbolic_discounting

---

### Appendix: Why these constants aren’t “from a book”

The structural pieces (EV core, risk aversion, discounting) are textbook ideas. The numerical constants (e.g., `5`, `0.1`, `0.8`) are **empirical**: they fit this game’s pacing and rewards. We recommend logging outcomes and refitting these coefficients periodically.
