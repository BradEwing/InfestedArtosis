# Suite A/B — ZvP thresholds across the 6-bot bench

Follow-up to `AB-RESULTS.md`, which tested Tomas Cere only. Three arms, 180 games each
(6 opponents x 30), all `--frozen --jobs 7 --timeout 1500`, identical learning snapshot.
540 games total. Two games excluded as symmetric crashes (see Exclusions).

| arm | engage/retreat (Protoss) | git rev | run id |
|---|---|---|---|
| BASELINE | 1.4 / 0.8 (pre-IA-311, pre-IA-281) | `aea84b3` | 20260904-224008 |
| CONTROL | 1.4 / 0.8 (as shipped) | `6e32a9d` | 20260904-192325 |
| ARM A | 0.9 / 0.5 | `e5ed047` | 20260904-210219 |

Arm B (0.655/0.37) was dropped: the Cere test tied it with arm A at p=1.000 on every endpoint,
and this bench carries fewer ZvP games than that test did, so it could not separate them either.

## Two design facts that govern how to read this

**1. Only a third of the suite is affected by the change.** The edit touches the `case Protoss:`
lines only. Ecgberht (Terran) and liongis (Zerg) contribute **zero** ZvP games and exist here purely
as a negative control — if they move, the change leaked. Dave Churchill, MadMixR and WillBot are
Random-race, so only some of their games are ZvP.

**2. Pooled ZvP win rate is confounded and must not be used as a headline.** The Random bots draw
their race per game, so each arm played a different number of ZvP games:

| ZvP games | BASELINE | CONTROL | ARM A |
|---|---|---|---|
| Tomas Cere | 30 | 30 | 30 |
| Dave Churchill | 9 | 8 | 18 |
| MadMixR | 6 | 11 | 14 |
| WillBot | 8 | 10 | 9 |
| **total** | **53** | **59** | **71** |

Arm A drew nearly twice Dave Churchill's ZvP games as BASELINE. Since non-Cere ZvP wins ~12% and
Cere wins ~90%, a different Cere/non-Cere mix moves the pooled number on its own. **All pooled ZvP
comparisons below are therefore stratified by opponent (Mantel-Haenszel), not raw.**

## Primary result — ZvP, stratified by opponent

| comparison | odds ratio | p |
|---|---|---|
| **CONTROL vs BASELINE** | **0.15** | **0.036** |
| ARM A vs BASELINE | 0.74 | 0.900 |
| ARM A vs CONTROL | 3.90 | 0.078 |

**This is the strongest statistical result in the whole investigation.** Stratifying by opponent
recovers the power that the raw pool throws away, and it shows what four per-opponent Fisher tests
individually could not: the shipped code is **significantly worse than pre-change across ZvP**
(p=0.036), and the retuned thresholds are **statistically indistinguishable from pre-change**
(p=0.900). Arm A vs CONTROL is directionally strong (OR 3.9) but short of significance at p=0.078.

Note this partly reverses `AB-RESULTS.md`, which concluded there was no demonstrated regression.
That conclusion was drawn from Cere alone at n=100. Widening to four ZvP opponents and stratifying
finds one. The regression is real; it was simply not visible in a single-opponent test.

### Per-opponent ZvP (W-L)

| opponent | BASELINE | CONTROL | ARM A | A vs CONTROL |
|---|---|---|---|---|
| Tomas Cere | 29-1 (96.7%) | 24-6 (80.0%) | 28-2 (93.3%) | p=0.254 |
| Dave Churchill | 1-8 | 0-8 | 1-17 | p=1.000 |
| MadMixR | 1-5 | 1-10 | 3-11 | p=0.604 |
| WillBot | 1-7 | 0-10 | 1-8 | p=0.474 |
| non-Cere pooled | 3-20 (13.0%) | 1-28 (3.4%) | 5-36 (12.2%) | p=0.389 |

Every slice shows the same ordering — BASELINE ≈ ARM A > CONTROL — and no individual slice reaches
significance. That consistency across four independent opponents is what the stratified test converts
into a real p-value.

### Negative control — the change did not leak

| slice | BASELINE | CONTROL | ARM A | A vs CONTROL |
|---|---|---|---|---|
| Ecgberht (Terran) | 12-18 | 13-17 | 14-16 | p=1.000 |
| liongis (Zerg) | 8-22 | 5-25 | 8-22 | p=0.532 |
| all non-Protoss | 47-80 | 43-77 | 41-67 | p=0.784 |

Nothing moves outside ZvP. The two-line edit is correctly scoped.

### Suite total (reported for completeness; ~2/3 noise by construction)

| arm | W-L | Wilson 95% |
|---|---|---|
| BASELINE | 79-101 (43.9%) | [36.8, 51.2] |
| CONTROL | 68-111 (38.0%) | [31.2, 45.3] |
| ARM A | 74-105 (41.3%) | [34.4, 48.7] |

All pairwise p>0.28. Do not use this as an endpoint.

## Behavioural endpoints — ZvP, split Cere vs non-Cere

Normalised per 1000 game frames where the metric is a rate, because non-Cere ZvP games run roughly
twice as long (mean ~19-22k frames vs Cere's ~9-11k).

### Tomas Cere

| metric | BASELINE | CONTROL | ARM A |
|---|---|---|---|
| games | 30 | 30 | 30 |
| mean end frame | 9,282 | 10,760 | 9,180 |
| engagements / game | 5.23 | 9.27 | 6.47 |
| RETREAT closes / 1k frames | 0.119 | 0.431 | 0.203 |
| **CONTAIN entries / 1k frames** | **0.13** | **5.43** | **1.67** |
| CONTAIN ≤2-frame share | 51.4% | 96.5% | 91.5% |
| **exchange (per game)** | **4.12** | **2.32** | **3.36** |
| exchange (per engagement) | 2.94 | 1.88 | 2.45 |
| verdicts/game: ENGAGE | 3.17 | 4.93 | 4.83 |
| verdicts/game: RETREAT | 0.90 | 4.00 | 1.53 |
| verdicts/game: ADVANCE | 1.13 | 0.23 | 0.10 |

### Non-Cere ZvP (Dave Churchill, MadMixR, WillBot)

| metric | BASELINE | CONTROL | ARM A |
|---|---|---|---|
| games | 23 | 29 | 41 |
| mean end frame | 18,544 | 21,624 | 19,121 |
| engagements / game | 11.52 | 17.66 | 15.54 |
| RETREAT closes / 1k frames | 0.455 | 0.638 | 0.653 |
| **CONTAIN entries / 1k frames** | **7.11** | **31.85** | **17.05** |
| CONTAIN ≤2-frame share | 93.6% | 97.6% | 97.8% |
| **exchange (per game)** | **0.816** | **0.584** | **0.610** |
| exchange (per engagement) | 0.797 | 0.718 | 0.650 |
| verdicts/game: ENGAGE | 3.57 | 6.52 | 6.15 |
| verdicts/game: RETREAT | 3.83 | 9.62 | 8.29 |
| verdicts/game: ADVANCE | 3.52 | 0.86 | 0.51 |

### What these say

**1. The threshold is the right lever for Cere and the wrong lever for everything else in ZvP.**
Against Cere, arm A recovers ~72% of the exchange gap (2.32 → 3.36 against baseline 4.12) and cuts
RETREAT verdicts 4.00 → 1.53. Against non-Cere ZvP it recovers ~11% of the exchange gap
(0.584 → 0.610 against baseline 0.816) and barely moves retreats (9.62 → 8.29). Same two constants,
opposite usefulness. This is the strongest confirmation yet of the investigation's claim that
non-Cere ZvP is an economy and composition problem — and note **even BASELINE only manages 0.816
exchange there, below break-even.** Those matchups were losing before IA-311 and remain losing after
any threshold value.

**2. The containment flap was amplified far more than anyone measured, and the threshold masks
rather than fixes it.** Against Cere: 0.13 → 5.43 entries per 1k frames, a **42x amplification** from
IA-311+IA-281 (the earlier estimates of 22x and 36x both came from accumulate-mode data and
understated it). Arm A cuts it to 1.67 — still **13x baseline**. Against non-Cere ZvP the absolute
rate is far worse: 7.11 → 31.85 → 17.05, with a 97.8% ≤2-frame share even in the best arm. The
`SquadManager.java:895` guard is worth materially more than the Cere-only data suggested, and it is
not substitutable by a threshold change.

**3. ADVANCE collapsed everywhere and no threshold restores it.** Cere 1.13 → 0.23 → 0.10;
non-Cere 3.52 → 0.86 → 0.51. Arm A makes it slightly *worse*, not better, in both populations.
ADVANCE is an approach/horizon decision rather than a ratio-vs-threshold decision, so this is an
**IA-281 effect that the IA-311 retune structurally cannot address**. It is the clearest attribution
signal available without a single-change arm, and it is consistent across two very different
opponent populations.

## Exclusions

Two games, one each in CONTROL (`KVDB1005`) and ARM A (`KVHVV00D`), both vs Dave Churchill, both
classified CRASH. Both verified **symmetric**: both players `is_crashed`, both `crashes_*` dirs
empty, no `Exception in thread` in either side's log, both frame logs stopping on the same frame
(316,896 in KVDB1005). Per the brief these are the game process dying under both bots, not a bot
defect. BASELINE had zero. Rate is ~1 per 270 games, matching the expected ~1 per few hundred.

## Correction to AB-RESULTS.md

`AB-RESULTS.md` attributed the Cere 40-10 (accumulate) vs 93-7 (frozen) gap to **accumulate mode**.
That is wrong. This suite ran Cere frozen on a mixed bench and got **24-6 (80%)** — matching the
accumulate figure exactly (p=1.000) and differing from the Cere-only frozen run (p=0.075). So the
distinguishing factor is not accumulate vs frozen.

I tested the two obvious mechanisms and ruled both out:
- **Not CPU load.** The mixed bench ran *faster*, median 49.5 fps vs 35.1 in the Cere-only run.
- **Not map allocation.** Losses are spread thinly across the 14-map pool in all three runs; no map
  dominates in any of them.

What remains is sampling variance at n=30-50, with the n=100 figure the most reliable single
estimate. Cere's true rate on shipped code is probably 85-90%, and individual batches bounce between
80% and 93%. The practical warning stands and is now better supported: **per-opponent conclusions
from a single batch of 30-50 games are fragile**, and that applies to this document's own
per-opponent rows as much as to the batch that started the investigation.

## Recommendation

Unchanged from `AB-RESULTS.md` and now better supported: **ship arm A, `engageThreshold(Protoss) =
0.9`, `retreatThreshold(Protoss) = 0.5`.**

What the suite adds:
1. The regression is now **statistically demonstrated** (CONTROL vs BASELINE, ZvP stratified,
   p=0.036) rather than merely directional, and arm A **restores baseline** (p=0.900).
2. The change is **correctly scoped** — no movement in 250+ non-Protoss games across three arms.
3. Codex's composition objection is **partly answered**: arm A does not harm the Hydralisk-capable
   non-Cere ZvP matchups (3-20 → 5-36 vs baseline, p=1.000), it simply does not help them either.
   A global Protoss constant appears safe on this bench; it is not sufficient.

Two things this suite promotes in priority:
- **The `SquadManager.java:895` containment guard.** 42x amplification against Cere, and a 97.8%
  ≤2-frame share against non-Cere ZvP that arm A only halves. Not substitutable by thresholds.
- **The ADVANCE collapse (IA-281).** Unrecovered in both ZvP populations by any threshold value.
  Worth its own single-change arm.

## Agent enrichment — containment flap (Pi)

Pi was dispatched to verify the flap mechanism in code and test whether it costs anything. It parsed
538 of the 540 games (the two symmetric crashes have no combat telemetry). Findings, with my
verification status:

**Corrections to the mechanism story — accepted.**
- Containment entry on a RETREAT *verdict* is at **`SquadManager.java:896`** (not :895); the forced
  FIGHT inside `evaluateContainingSquad` is at **:1055-1061** (not :1051-1058), and it runs **every
  frame**, ahead of the 48-frame re-evaluation gate at :1063. `enterContainment` sets a 120-frame
  lock at :1045-46.
- **The oscillation is CONTAIN→FIGHT→CONTAIN, not CONTAIN→FIGHT→RETREAT.** Squad *status* RETREAT is
  almost never entered, because :896 intercepts the verdict first — one sample game shows 1,155
  FIGHT→CONTAIN against 10 RETREAT→CONTAIN. This corrects both my description above and the original
  investigation's. The sim *verdict* RETREAT is still the trigger, so the causal chain I described
  holds; the status-level wording did not.
- `fightLockHolds` (:938-943) does not damp the cycle — it yields exactly when the enemy is measured
  and the ratio is below the retreat threshold.

**New quantification.**
- Dwell is degenerate: median and p90 are **1 frame** in every arm, 95-98% of episodes last exactly
  one frame, and there are **zero episodes longer than 1400 frames** — the containment timeout path
  is unreachable. Containment as a *stance* effectively does not exist in this build.
- 98.5-99.6% of CONTAIN→FIGHT transitions occur **inside** engagement windows. Only 0.5-1.3% are
  "orphan" flaps between fights. This is decision thrashing during combat, not idle churn.
- CONTAIN→FIGHT totals (ZvP): BASELINE 36, CONTROL 1,749, ARM A 458.

**On cost — suggestive, explicitly not causal.** Within-arm per-game Spearman of flap rate against
exchange is −0.81 / −0.68 / −0.80 (n=53/59/71, permutation p≈2e-4). Won vs lost mean flap per 1k
frames is 0.86 vs 14.82 (Cere CONTROL). Unit death rate rises 17.7% → 85.2% across
`status_change_count` buckets. Pi is appropriately careful that losing produces longer, more
pressured games which produce more flap, so the arrow may run either way; controlling for engagement
duration drops the churn coefficient from 0.83 to 0.44. **The damage remains inference.**

**Why non-Cere is ~6x worse.** Mostly game state — the bot wins 80% of Cere games at a median 9,566
frames but 3.4% of non-Cere ZvP at 22,167. But a residual survives normalisation: per 1k *combat*
frames the gap is still 50.0 vs 11.7, tracking a **2.8x larger enemy force at the containment point**
(enemy supply believed at entry 30.2 vs 10.9). More enemies inside the 512 detection radius means the
forced FIGHT fires on essentially every contain.

**One non-reproduction, resolved in my favour.** Pi could not reproduce my `RETREAT closes` figures
under any squad-decision transition convention. That is a source mismatch: the metric comes from
`telemetry_engagements.csv`'s `squad_status_close` column, not from transitions in
`telemetry_squad_decisions.csv`. I re-verified it independently — 0.431 / 0.203 / 0.119 per 1k
frames for CONTROL / ARM A / BASELINE against Cere, exactly as tabulated above. The numbers stand.
Pi also confirmed my rates are pooled (Σ/Σ) rather than mean-of-games, and reproduced every other
figure exactly, including all four exchange values.

**Pi's recommendation:** prioritise the guard at medium-high, targeting the cycle rather than the
stance. Cheapest correct shape is to refuse containment entry at :896 when close enemies are already
present, or to require K consecutive frames of close enemies before the forced FIGHT while the
containment lock is fresh. It also recommends adding close-enemy-count-at-entry telemetry and
re-running behind a runtime flag, since the correlations bound the upside without proving it.

## What this suite could not determine

- **Whether arm A helps non-Cere ZvP at all.** 1-28 → 5-36 is p=0.389 on a base rate near 3-12%.
  Settling it needs several hundred non-Cere ZvP games, which at ~35% Protoss draw means ~1,000+
  games against the Random bots.
- **Whether A or B is the better constant.** Not tested here; the Cere test could not separate them
  and this bench has less ZvP volume.
- **Why non-Cere ZvP loses at 13% even on BASELINE.** Out of scope for a threshold experiment;
  dispatched to a separate agent investigation.
- **Attribution between IA-311 and IA-281.** BASELINE removes both. The ADVANCE evidence implicates
  IA-281 for that specific channel, but outcome attribution still needs a single-change arm.
- **Anything about VOID**, excluded from this bench by request.

## Reproduction

```
py scripts/batch/run.py "Dave Churchill" "MadMixR" "liongis" "Ecgberht" "WillBot" "Tomas Cere" \
   -n 30 --jobs 7 --timeout 1500 --frozen --deploy-jar
```

Arms are identified by `git_rev` in `%APPDATA%\scbw\batches\<run_id>.json`. BASELINE was built from
temp branch `ab-baseline-aea84b3` at `aea84b3`; arm commits on `BradEwing/ab-cere-thresholds` were
not disturbed. `mvn -o clean package` and `mvn -o checkstyle:check` exited 0 for all three arms.
