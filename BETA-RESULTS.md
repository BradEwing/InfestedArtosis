# Beta integration results — IA-313 + IA-314

Jar `b787374` (merge of `3b21495` IA-313 and `85867b1` IA-314 onto `a61cffd`, tip of `origin/main`).
400 games, accumulate mode, learning wiped before the first game, 2026-09-05.
Baseline: Arm A jar `e5ed047`, 394 games, same conditions, same day.

Every number below is VERIFIED — recomputed from the batch manifests and per-game telemetry by
`mech.py` / `compare.py`, with both arms passed through the same code path, the same 50-game cap and
the same 25-minute cull. INFERRED claims are labelled inline. Where the brief quotes a baseline
figure I quote my recomputation next to it, because two of them were measured differently (see
*Metric definitions* at the end).

## Read this first: the two changes cannot be told apart by win rate

Both tickets state explicitly that their measurement batches must be run separately — IA-313:
"a batch measuring both at once cannot attribute the result. Run them separately." IA-314 says the
same. The brief directed a single combined batch, so that is what ran, and **no per-opponent win-rate
delta below can be attributed to either ticket individually.**

What does survive the combination is the mechanism evidence, because the two tickets move disjoint
quantities: IA-313 moves drone-plan cancellations and drone counts, IA-314 moves the zergling demand
and hence tech-unit output. Those are reported separately and are attributable. The bench win rate
is not.

## Headline: no significant change in bench win rate

| bot | baseline | beta | base WR | beta WR | delta | Fisher p |
|---|---|---|---|---|---|---|
| GuiBot | 50-0 | 50-0 | 100% | 100% | +0 | 1.000 |
| Tomas Cere | 50-0 | 44-6 | 100% | 88% | -12 | 0.027 |
| Zealot Hell | 2-48 | 4-46 | 4% | 8% | +4 | 0.678 |
| Lukas Moravec | 35-15 | 29-21 | 70% | 58% | -12 | 0.298 |
| Prism Cactus | 25-23 | 46-4 | 52% | 92% | +40 | 9.9e-06 * |
| WuliBot | 2-46 | 1-49 | 4% | 2% | -2 | 0.613 |
| Andrew Smith | 13-37 | 15-35 | 26% | 30% | +4 | 0.824 |
| Dave Churchill | 10-38 | 10-40 | 21% | 20% | -1 | 1.000 |
| **TOTAL** | **187-207** | **199-201** | **47.5%** | **49.8%** | **+2.3** | **0.524** |

Total: 47.5% [42.6, 52.4] to 49.8% [44.9, 54.6], p=0.524. The intervals overlap almost entirely.
**The bench win rate did not move.**

Only **Prism Cactus** (+40pp, p=9.9e-06) survives Bonferroni correction for 8 tests (p < 0.0063).
Tomas Cere at p=0.027 does not, and the brief's own warning applies directly to it — the same code
swung 80% to 93% between batches on this opponent. I do not consider the Cere drop established.

Power is the limiting factor: at n=50 per opponent only large moves are detectable. Three of my own
mid-run readings reversed as n grew (Andrew Smith −18 → +4, Lukas Moravec −27 → −12, Prism Cactus
held). Single-batch per-opponent differences on this bench are noisy and should be treated as such.

Dave Churchill by race — baseline ZvP 1-13, ZvT 1-18, ZvZ 8-7; beta ZvP 4-15, ZvT 2-15, ZvZ 4-10.
None significant (p=0.366 / 0.593 / 0.264). IA-314 touches `ProtossBase` only, so ZvT and ZvZ are its
control, and the control holds.

## IA-313: unambiguous mechanism success

| metric | baseline | beta | verdict |
|---|---|---|---|
| `REACTION_EARLY_RUSH_DRONE` cancels/game vs WuliBot, mean | 563.7 | **2.9** | AC <50: **pass** |
| same, median | 7.0 | 0.0 | |
| same, max over 50 games | 3525 | **68** | |
| games in the livelock mode (>50 cancels) | 19/48 (40%) | **1/50 (2%)** | p=1.9e-06 |
| living drones at frame 8,000 vs Zealot Hell, median | 7.0 | **10.0** | AC rise: **pass** |
| Lair completions vs Zealot Hell | 6/50 | **18/50** | p=0.0091, AC >7/52: **pass** |
| Spire completions vs Zealot Hell | 2/50 | **9/50** | p=0.051, AC >2/52: **pass** |
| win rate vs WuliBot | 2-46 | 1-49 | AC "not worse": p=0.613, **holds** |
| win rate vs Zealot Hell | 2-48 | 4-46 | AC "not worse": p=0.678, **holds** |

**The livelock is gone.** The baseline distribution was bimodal, not centred on its mean — 29/48
WuliBot games sat at 0-7 cancels and 19/48 ran 32-3525, so the quoted "~520/game" was an average over
two populations. The right question was whether the runaway mode still occurs, and it does not: one
game of 50 exceeded 50 cancels, at 68, against a baseline maximum of 3525. That is a 3-order-of-
magnitude reduction in the tail, at p=1.9e-06.

The downstream economy moved with it, which is the confirmation that the cancels mattered rather than
merely being logged: drones at frame 8,000 vs Zealot Hell rose, and Lair completions tripled
(6/50 to 18/50). IA-313 meets every one of its acceptance criteria.

## IA-314: hydralisks fixed, mutalisks regressed

This is a split result and the negative half is real.

Within each named build, bench-wide — the like-for-like test of "does the named build build its
named unit":

| build | arm | n | titular unit/game | games with >=1 | Spire |
|---|---|---|---|---|---|
| 3HatchHydra | baseline | 31 | 0.65 hydralisks | 4/31 | - |
| 3HatchHydra | **beta** | 52 | **8.60 hydralisks** | 16/52 | - |
| 3HatchMuta | baseline | 81 | 6.51 mutalisks | 43/81 | 47/81 |
| 3HatchMuta | **beta** | 53 | **1.74 mutalisks** | 14/53 | 21/53 |

- **Hydralisks: fixed.** 0.65 to 8.60 per game, clearing the AC's "at least 8 hydralisks per game".
  `3HatchHydra` went from **0-31 to 26-26** (p=2.6e-07) — a build that had never won a game on this
  bench now breaks even. Vs WuliBot specifically the AC is missed (0.08 to 2.14/game, AC wanted >=8),
  but the bench-wide within-build figure meets it.
- **Mutalisks: regressed.** 6.51 to 1.74 per game, Spire completion within the build 58% to 40%, and
  `3HatchMuta` fell from **26-55 (32%) to 6-47 (11%)**, p=0.0068. The AC asked for >=5 mutalisks per
  game vs WuliBot; the observed figure is **0.00**. This is a clear miss and a clear negative.

Bench-wide, mutalisks fell from 1.34 to 0.23 per game and hydralisks from 3.39 to 1.83, while
zerglings rose from 52.1 to 58.9 per game. **INFERRED:** those bench-wide totals are heavily
confounded — the bandit explored a materially different opener mix in the two arms (Overpool 166→64,
12Pool 43→101, 12Hatch 9→33), and `3HatchMuta` fell from 81 to 53 games while `3HatchHydra` rose from
31 to 52. Build selection, not only build behaviour, differs. The within-build table above is the
sounder comparison, and it still shows the muta regression.

The mechanism for the muta half was anticipated by IA-314's own Dependencies note: the crowded-out
tech is *also* gated elsewhere, and `ThreeHatchMuta.java:94` requires `baseCount >= 2` plus 16 living
drones for a Spire. Freeing the queue was not sufficient there. That note said this outcome "is the
next thing to look at rather than a reason to widen this ticket" — it should now be looked at.

## What I would conclude

- **IA-313 should ship.** It meets every acceptance criterion, the livelock is eliminated at
  p=1.9e-06, and the economic consequences follow in the expected direction. It does not by itself
  win more games on this bench, which is consistent with it removing a failure mode rather than
  adding strength.
- **IA-314 should not ship as-is.** It fixes hydralisk production convincingly and turns
  `3HatchHydra` from 0-31 into 26-26, but it costs `3HatchMuta` 21 points of win rate (p=0.0068) and
  most of its mutalisk output. Net bench effect is a wash. The hydra half is worth keeping; the muta
  regression wants a separate look at the Spire gate before this goes to the ladder.
- **The combined batch cannot separate them.** If the muta regression is to be pinned on IA-314
  rather than IA-313, that needs the separate batch both tickets asked for.

## Method and caveats

- Run ids `20260905-141704` (400 games, `status=completed`) and `20260905-160649` (top-up).
  Manifest verified `git_rev=b787374, frozen=false, jobs=8, n=50, timeout=1500`. All 8 opponents
  confirmed launching by direct container inspection, so the `--jobs` starvation failure that cost a
  batch on 2026-09-05 is not present here. 1 game culled at >=25 min; 3 non-WIN/LOSS outcomes
  (baseline had 14).
- Docker never failed during this run — `run.py` completed all 400 games unaided. The supervisor was
  started afterwards by my watchdog and began a redundant second batch before I stopped it; the few
  top-up games it produced are included only where an opponent was short of 50, the same multi-run
  composition the baseline itself used (4 runs, 394 games).
- **Metric definitions.** Two brief-quoted baselines were measured differently from mine and I
  recomputed both arms rather than reconcile them. Drones at frame 8,000 vs Zealot Hell: the brief
  cites 2-3, a median over the 2,400-9,600 frame window; mine is a point-in-time count of completed
  drones minus destroyed and morphed, which puts the baseline at 7.0. Muta/hydra per game vs WuliBot:
  the brief cites 0.1 and 0.04, my recomputation of the same arm gives 0.04 and 0.08. In both cases
  the comparison above uses my recomputation on **both** arms, so the delta is apples-to-apples even
  where the absolute level differs from the brief.
- The baseline record, opener table, build-order table and Dave-by-race split reproduce the brief's
  figures exactly through this code path, which is the check that the tooling agrees with the
  previous run.
