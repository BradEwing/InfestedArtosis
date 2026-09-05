# A/B test — ZvP engage/retreat thresholds vs Tomas Cere

> **Superseded in two places by `AB-SUITE-RESULTS.md`** (6-bot bench, 540 games). Read that first.
> 1. This document concludes there is no statistically demonstrated regression. That was true of
>    Cere alone at n=100. Across four ZvP opponents, stratified by opponent, CONTROL vs BASELINE is
>    **p=0.036** — the regression is real and this document simply lacked the power to see it.
> 2. This document attributes the 40-10 vs 93-7 Cere gap to **accumulate mode**. That is wrong. A
>    frozen mixed-bench run also scored 24-6 (80%), matching accumulate exactly. Neither CPU load
>    nor map allocation explains it; it is sampling variance at n=30-50.
>
> Everything else below stands, including the recommendation.

Four arms, 100 games each, all `--frozen --jobs 7 --timeout 1500` vs Tomas Cere, identical learning
snapshot (`read_rows_before` = 1107 every run). 400 games, 0 crashes, 0 NO_RESULT, 400/400 scored.
Opener was 4Pool in 100/100 games in every arm, so no strategy confound anywhere.

The BASELINE arm was added after the first two arms had run, on instruction from the orchestrator,
once CONTROL came in far above its predicted value. It is the most informative arm in the experiment.

## Arms

| arm | engage | retreat | git rev | run id | W-L | win rate, Wilson 95% |
|---|---|---|---|---|---|---|
| BASELINE (pre-IA-311/281) | 1.4 | 0.8 | `aea84b3` | 20260904-155146 | 98-2 | 98.0% [93.0, 99.4] |
| CONTROL (as shipped) | 1.4 | 0.8 | `6e32a9d` | 20260904-132657 | 93-7 | 93.0% [86.3, 96.6] |
| A (Claude) | 0.9 | 0.5 | `e5ed047` | 20260904-141733 | 98-2 | 98.0% [93.0, 99.4] |
| B (Codex/Pi) | 0.655 | 0.37 | `8c1b201` | 20260904-150400 | 98-2 | 98.0% [93.0, 99.4] |

BASELINE is `release 0.63`, the tree immediately before both IA-311 and IA-281. It carries the
unmodified 1.4/0.8 constants on the *original* strength scale they were calibrated for.

## Primary endpoint — win rate, Fisher exact two-sided

| comparison | p |
|---|---|
| A vs CONTROL | 0.170 |
| B vs CONTROL | 0.170 |
| A vs B | 1.000 |
| A vs BASELINE | 1.000 |
| B vs BASELINE | 1.000 |
| **CONTROL vs BASELINE** | **0.170** |

Neither arm beats CONTROL at p<0.05. Under the decision rule as pre-registered that is outcome 3.
**That framing is misleading here and should not be used.** The pre-registered power table assumed
CONTROL ≈80%; CONTROL actually came in at 93%. Recomputed headroom against 93-7:

| arm result | p vs CONTROL |
|---|---|
| 96-4 | 0.54 |
| 98-2 | 0.17 |
| 99-1 | 0.065 |
| **100-0** | **0.014** |

Only a flawless 100/100 clears p<0.05. Arms A and B scored 98-2 — within two games of the maximum
achievable result — and still cannot reach significance. The win-rate endpoint is saturated, not
failed. Both arms hit the ceiling of what this design can measure.

### The premise of the experiment is in doubt

This test was commissioned to fix an 18-point Cere regression attributed to IA-311. Under frozen
conditions that regression does not reproduce:

- CONTROL (same code that scored 40-10 in accumulate batch `20260904-031526`) scored **93-7**.
- CONTROL vs that 40-10: **p=0.0272**. Identical code, identical opener, identical build order.
- CONTROL vs the pre-IA-311 batch-18 figure (56-1): **p=0.2597 — not significant**.
- BASELINE, the actual pre-change code measured under identical frozen conditions: **98-2**.
- CONTROL vs BASELINE: **p=0.170 — not significant.**

The 98%→80% drop that motivated the whole investigation was most likely an unlucky draw and/or a
mixed-bench load effect in accumulate mode, not a code regression. The honest statement is that
**there is no statistically demonstrated Cere regression from IA-311 at n=100.** The direction is
consistently unfavourable (93 < 98 across every behavioural endpoint below), but the effect on games
won is not distinguishable from noise at this sample size.

## Secondary endpoints — promoted to primary

These are per-engagement and per-decision, with thousands of rows, and they carry the real signal.

| endpoint | BASELINE | CONTROL | ARM A | ARM B |
|---|---|---|---|---|
| engagements total | 556 | 730 | 549 | 539 |
| engagements / game | 5.56 | 7.30 | 5.49 | 5.39 |
| RETREAT closes / game | 1.06 | 2.80 | 1.29 | 1.14 |
| status_change_count / game | 6.18 | 8.40 | 4.79 | 5.12 |
| CONTAIN entries / game | 1.14 | 24.87 | 3.30 | 3.63 |
| CONTAIN ≤2-frame share | 61.4% | 94.6% | 85.5% | 83.7% |
| decision rows / game | 213.1 | 199.2 | 111.3 | 151.7 |
| exchange (per engagement) | 2.93 | 2.76 | 3.13 | 2.98 |
| **exchange (per game)** | **4.26** | **3.69** | **4.26** | **4.34** |

### Verdict mix at engagement open

| arm | ENGAGE | RETREAT | ADVANCE | NONE |
|---|---|---|---|---|
| BASELINE | 342 | 72 | 137 | 5 |
| CONTROL | 431 | 277 | 14 | 8 |
| ARM A | 435 | 105 | 9 | 0 |
| ARM B | 443 | 81 | 11 | 4 |

### Decision-row verdict mix

| arm | ENGAGE | RETREAT | ADVANCE | NONE | total |
|---|---|---|---|---|---|
| BASELINE | 922 | 934 | 2120 | 17338 | 21314 |
| CONTROL | 1524 | 4547 | 1936 | 11916 | 19923 |
| ARM A | 1192 | 1322 | 1450 | 7166 | 11130 |
| ARM B | 1107 | 1273 | 1324 | 11465 | 15169 |

Three things fall out of these two tables.

**1. The retreat inflation is real, large, and the thresholds fix it.** RETREAT decision rows go
934 (BASELINE) → 4,547 (CONTROL), a **4.9× increase** from IA-311+IA-281 alone. Arms A and B pull it
back to 1,322 and 1,273 — within ~40% of baseline. At engagement open the same shape appears:
72 → 277 → 105/81, with arm B landing closest to baseline. This is the mechanism the investigation
predicted, confirmed against a proper frozen baseline. It is the strongest result in this experiment.

**2. The containment flap is largely caused by the change, and largely fixed by the thresholds —
but not entirely.** CONTAIN entries/game go 1.14 → 24.87, a **21.8× amplification** (the investigation
estimated ~36× from accumulate-mode data). Arms cut it to 3.30/3.63 without touching `SquadManager`.
That is mechanistically coherent: `SquadManager.java:895` enters containment on a RETREAT verdict, so
starving RETREAT starves the flap. But 3.3 is still **~3× baseline**, and the ≤2-frame share stays at
84-86% against baseline's 61%. **The threshold retune does not fully close it — the separate guard at
`SquadManager.java:895` is still warranted.**

**3. ADVANCE never comes back, and no threshold can restore it.** ADVANCE at engagement open collapses
137 → 14 and neither arm recovers it (9, 11). Decision-row ADVANCE shows the same: 2,120 → 1,936 →
1,450/1,324, i.e. the arms make it *worse*, not better. ADVANCE is a horizon/approach decision, not a
ratio-vs-threshold decision, so this is an **IA-281 effect that the IA-311 threshold retune cannot
address**. Anyone expecting the threshold fix to restore pre-change behaviour wholesale should not.

### Exchange rate by `sim_ratio_open` bucket

n / exchange. **BASELINE ratios are on the pre-IA-311 strength scale** (ZvP ×0.468) and are *not*
comparable bucket-for-bucket with the other three; its row is shown for completeness only.

| bucket | BASELINE | CONTROL | ARM A | ARM B |
|---|---|---|---|---|
| 0.0-0.5 | 173 / 2.68 | 150 / 1.28 | 74 / 1.72 | 70 / 1.70 |
| 0.5-0.8 | 13 / 1.94 | 54 / 0.88 | 33 / 1.06 | 35 / 1.47 |
| 0.8-1.0 | 0 / – | 26 / 2.39 | 14 / 1.35 | 18 / 0.95 |
| 1.0-1.2 | 9 / 1.95 | 22 / 1.97 | 19 / 1.94 | 11 / 1.58 |
| 1.2-1.4 | 12 / 1.29 | 39 / 1.47 | 29 / 1.42 | 28 / 1.26 |
| 1.4-1.8 | 10 / 1.43 | 35 / 1.24 | 46 / 2.47 | 26 / 1.59 |
| 1.8+ | 332 / 3.42 | 396 / 4.90 | 334 / 4.75 | 347 / 4.43 |

Both arms roughly **halve** the number of engagements opened below ratio 0.5 (150 → 74/70) while
*improving* the exchange in that bucket (1.28 → 1.72/1.70). The change is not "fight more" — it
re-sorts which fights happen. Middle buckets remain thin (n=11-46) and non-monotonic in every arm;
the investigation's "break-even crosses at ≈0.8" claim is neither confirmed nor refuted here.

### Probes killed / Zerglings lost by frame 8000, split by outcome

| arm | wins n | Probes (win) | lings lost (win) | losses n | Probes (loss) | lings lost (loss) |
|---|---|---|---|---|---|---|
| BASELINE | 98 | 15.76 | 10.68 | 2 | 2.50 | 18.50 |
| CONTROL | 93 | 15.12 | 9.17 | 7 | 3.29 | 15.71 |
| ARM A | 98 | 15.94 | 10.49 | 2 | 2.50 | 14.50 |
| ARM B | 98 | 16.22 | 10.68 | 2 | 4.50 | 20.00 |

**The "second failure mode" is not new.** The investigation identified losses with ~3.3 Probes killed
and ~18.3 lings lost as a distinct failure mode introduced by IA-311. BASELINE losses show the same
signature (2.50 Probes, 18.50 lings). The mode is **pre-existing**; what differs between arms is only
how often it occurs (7 games vs 2), and that difference is not significant (p=0.170). Win-side numbers
are indistinguishable across all four arms (15.1-16.2 Probes), reproducing the investigation's own
finding that the wins were never degraded.

## Exploratory: pooled arms

A and B are identical on the primary endpoint (p=1.000), so pooling them is defensible as a
**post-hoc, not pre-registered** analysis. Flagged as exploratory; do not treat as confirmatory.

| comparison | p |
|---|---|
| A+B (196-4) vs CONTROL (93-7) | **0.046** |
| A+B (196-4) vs BASELINE (98-2) | 1.000 |
| BASELINE+A+B (294-6) vs CONTROL (93-7) | **0.022** |

At n=200 the retuned thresholds are marginally better than shipped, and indistinguishable from the
pre-change baseline. This is the clearest statement the data supports: **the arms restore baseline
behaviour, and shipped CONTROL is the outlier.**

## Recommendation

**Ship arm A: `engageThreshold(Protoss) = 0.9`, `retreatThreshold(Protoss) = 0.5`.**

Reasoning, in order:

1. Both arms restore baseline win rate exactly (98-2, identical to BASELINE, p=1.000) and restore
   per-game exchange to baseline (4.26/4.34 vs baseline 4.26, against CONTROL's 3.69).
2. A and B are statistically indistinguishable on every endpoint. Per the pre-registered tiebreak,
   exchange decides: A wins per-engagement (3.13 vs 2.98), B wins per-game (4.34 vs 4.26) — a split
   of ~2% either way, i.e. a tie. The rule's final fallback then applies: **prefer A**, which has the
   stronger physical derivation.
3. A retains a larger safety margin than B. B's 0.655 sits at the bottom of the tested range; if the
   effective-HP repricing is ever revisited, 0.9 degrades more gracefully than 0.655.

Caveat on shipping: this is one opponent, bandit-locked to 4Pool, on a Zergling-only composition.
Codex's scope objection stands unaddressed — a Hydralisk-heavy ZvP maps to ~0.99 rather than ~0.65,
so a global Protoss constant may be wrong for later-game compositions. **Validate on the full bench
before merging**, and treat the ZvP threshold as a candidate for composition-awareness.

Secondary recommendation: **keep the `SquadManager.java:895` containment guard on the backlog.** The
threshold retune cuts the flap 24.9 → 3.3 entries/game but leaves it ~3× baseline with an 85%
≤2-frame share. The threshold masks the flap; it does not fix it.

## What this test could not determine

- **Whether IA-311 caused a real regression at all.** CONTROL vs BASELINE is p=0.170. The effect is
  directionally consistent across every behavioural endpoint but not significant on games won. It
  would take roughly n≈400/arm to resolve a 93% vs 98% difference at p<0.05.
- **Whether A or B is the better constant.** p=1.000 on the primary endpoint and a split decision on
  exchange. The 37% difference between 0.9 and 0.655 is invisible at this n against this opponent,
  because both sit far enough below the old effective bar (2.99) that Cere's 4Pool engagements clear
  either. Distinguishing them needs an opponent whose engagements actually fall *between* 0.655 and
  0.9 — Cere is not that opponent.
- **Whether the thresholds help anywhere other than Tomas Cere.** Single-opponent test by design.
  The investigation's finding that non-Cere ZvP is 3-48 and unmoved by any threshold change was not
  re-tested here and remains the larger open problem.
- **The correct exponent for the effective-HP term.** Untouched by this experiment.
- **Attribution between IA-311 and IA-281.** BASELINE removes both at once. The ADVANCE collapse
  (137 → 14, unrecovered by either arm) is strong circumstantial evidence that IA-281 owns a distinct
  slice of the behaviour change, but a single-change arm is still the only way to attribute outcomes.
- **Why accumulate mode produced 40-10 on this exact code.** The 93-7 vs 40-10 gap (p=0.0272) on
  identical revisions is unexplained. Candidates: bandit/learning-state drift, or seven-opponent
  load affecting game stability. This matters beyond this ticket — **if accumulate-mode batches carry
  this much extra variance, every per-opponent conclusion drawn from them is weaker than it looks.**
  Worth its own investigation.

## Reproduction

```
py scripts/batch/run.py "Tomas Cere" -n 100 --jobs 7 --timeout 1500 --frozen --deploy-jar
```

Arms are distinguished by `git_rev` in `%APPDATA%\scbw\batches\<run_id>.json`. The BASELINE arm was
built from a temporary branch `ab-baseline-aea84b3` at `aea84b3`; the arm commits on
`BradEwing/ab-cere-thresholds` were not disturbed. `mvn -o clean package` and `mvn -o checkstyle:check`
exited 0 for all four arms (501 tests on the three post-change arms, 485 on BASELINE).
