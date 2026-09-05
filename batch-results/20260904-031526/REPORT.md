# Batch 20260904-031526 — IA-311 + IA-281 integration

Jar 0.63 (`f55c42450b95`) · git `6e32a9d` · 350 games · 7 opponents × 50 · `--jobs 7 --timeout 1500`
· accumulate mode · 14 maps · started 03:15:26, finished 07:04:16 (3h49m).

Baseline throughout is batch `20260903-210912` (`6d09bf8`), 345 decided games, same bench, same maps,
same flags.

**This run contains both changes. No outcome below can be attributed to IA-311 or IA-281
individually.** Where a mechanism *is* separable, it is called out explicitly and the reasoning given.

---

## Headline

| | This run | Batch 18 | Δ | 95% CI (Wilson) | Baseline inside CI? |
|---|---|---|---|---|---|
| Overall | **40%** (140-207, n=347) | 46.5% | −6.2 | [35.3, 45.6] | **no** |
| Ex-Tomas Cere | **34%** (100-197, n=297) | 36.0% | −2.3 | [28.5, 39.2] | yes |

The overall drop is **entirely Tomas Cere**. Once Cere is removed the batch sits on baseline.

## Per opponent

| Opponent | W-L | n | WR | 95% CI | Batch 18 | Δ | Baseline in CI? |
|---|---|---|---|---|---|---|---|
| Tomas Cere | 40-10 | 50 | 80% | [67.0, 88.8] | 98% | **−18.0** | **no** |
| VOID | 31-19 | 50 | 62% | [48.2, 74.1] | 59% | +3.0 | yes |
| Ecgberht | 18-31 | 49 | 37% | [24.7, 50.7] | 43% | −6.3 | yes |
| MadMixR | 15-35 | 50 | 30% | [19.1, 43.8] | 41% | −11.0 | yes |
| Dave Churchill | 14-35 | 49 | 29% | [17.8, 42.4] | 26% | +2.6 | yes |
| liongis | 14-36 | 50 | 28% | [17.5, 41.7] | 39% | −11.0 | yes |
| WillBot | 8-41 | 49 | 16% | [8.5, 29.0] | 14% | +2.3 | yes |

**Tomas Cere is the only per-opponent move that clears its interval.** MadMixR −11 and liongis −11
look large but both baselines sit inside the CI at n=50; per the brief's noise-floor caution these
are intervals, not results.

## Dave Churchill race split — the point of the run

Race derived per game from the first `player_owned=true` unit type in `logs_1/unit_events.csv`
(the configured race is `Random`; `opponent_race` in the dataset is the *configured* value and
cannot be used). All 50 games resolved.

| | This run | Batch 18 |
|---|---|---|
| ZvP | **1W-17L** (5.6%) | 0W-12L (0%) |
| ZvT | **3W-9L** (25%) | 0W-13L (0%) |
| ZvZ | **10W-9L** (53%) | 13W-12L (52%) |

### ZvP exchange rate — IA-311's stated acceptance criterion

Criterion: *"reports the ZvP exchange rate above the measured 0.16 baseline."*
Recomputing the metric on batch 18 reproduces the ticket's figure exactly (0.162), so the
definitions match and the comparison is clean. Bootstrap CIs, 4000 resamples.

| | Batch 18 | This run |
|---|---|---|
| ZvP | 0.162 [0.104, 0.219] n=12 | **0.108 [0.068, 0.187] n=18** |
| ZvT | 0.255 [0.187, 0.333] n=13 | 0.485 [0.253, 1.054] n=12 |
| ZvZ | 0.743 [0.579, 0.914] n=25 | 0.957 [0.775, 1.165] n=19 |

**The criterion is not met.** The ZvP point estimate moved *down*, 0.162 → 0.108. The intervals
overlap heavily, so the honest reading is "unchanged, possibly worse" — not "significantly worse."
Either way there is no evidence of the intended improvement.

### ZvZ regression canary

**10W-9L (53%) against the 13W-12L (52%) baseline — flat.** The mirror exchange rate improved
(0.743 → 0.957). IA-311's effective-HP term is supposed to cancel in the mirror by construction,
and it did. **No implementation-fault signal.**

---

## What the telemetry says the changes actually did

Both changes are visibly working at the mechanism level, from `dataset_engagements.csv`:

| vs Tomas Cere (ZvP) | Batch 18 | This run |
|---|---|---|
| engagements | 311 | 456 |
| `sim_ratio_open` median | 19.36 | **2.13** |
| verdict ENGAGE / RETREAT | 193 / 41 | 268 / **165** |
| status_close FIGHT / RETREAT | 267 / 43 | 242 / **213** |
| verdict ADVANCE | 72 | **11** |

| vs Dave Churchill | Batch 18 | This run |
|---|---|---|
| `sim_ratio_open` median | 1.236 | **0.693** |
| verdict ADVANCE | 185 | **11** |
| verdict RETREAT | 219 | 374 |
| status_close FIGHT | 100 | 88 |

Two readings, with different attribution confidence:

1. **The ADVANCE collapse (185 → 11 vs DC, 72 → 11 vs Cere) is attributable to IA-281.** ADVANCE is
   issued precisely when no enemy was measured; widening the horizon makes those enemies
   measurable, so the class nearly disappears. This is IA-281's second acceptance criterion
   ("the share of ADVANCE decisions that meet an enemy within 10 seconds falls well below 93.1%")
   satisfied by near-elimination of the class itself.

2. **The `sim_ratio` collapse is NOT attributable to either ticket alone.** IA-311 raises Protoss
   unit strength (lowering the ratio in ZvP) and IA-281 counts more enemy units (lowering the ratio
   everywhere). Both push the same direction. IA-311's criterion 4 — "the ZvP `sim_ratio`
   distribution shifted downward and the count of FIGHT transitions reduced" — is satisfied in
   observation, but this batch cannot show it was IA-311 that did it.

### The Tomas Cere finding

Cere is a **pure 4Pool ZvP matchup** — the bandit plays 4Pool in 50/50 games, in both batches. The
opener did not change; the in-game behaviour did. Against Cere, `RETREAT` closes went **43 → 213**
(≈5×) while the win rate went 98% → 80%.

The plausible mechanism is that a 4Pool all-in only wins by committing, and a combat sim that now
prices a Zealot far above a Zergling withholds that commit. That is IA-311's intended effect
producing an unintended outcome in the one matchup where the all-in was already winning. It is a
hypothesis consistent with the telemetry, **not** a demonstrated cause — and because IA-281 also
depresses `sim_ratio`, this batch cannot separate the two.

This is the most actionable result in the run and is worth a ticket regardless of what happens to
IA-311 and IA-281.

---

## Harness failures

Three games did not produce a clean bot result. **None is a bot defect.**

**2 crashes, both the symmetric signature** the brief describes — the harness labels these
"JVM died mid-game" because `classify()` returns CRASH from `result.json` before running
`jvm_died()`; that label is a bucket name, not a diagnosis.

| Game | Opponent | Evidence |
|---|---|---|
| `KU4HQ06A` | WillBot | no `Exception in thread` in either bot.log; both frame logs stop at frame 257472; `crashes_0`/`crashes_1` empty; winner and loser both null |
| `KU4HQ015` | Dave Churchill | no `Exception in thread` in either bot.log; both frame logs stop at frame 45264; `crashes_0`/`crashes_1` empty; winner and loser both null |

Both are the game process going down under both bots.

**1 failed launch** — `KU4HQ049`, Ecgberht on (4)Roadrunner, `scbw.play` exit 1 after 166s wall:

```
scbw.error.ContainerException: One lingering container has been found after
single container timeout (70 sec), the game probably crashed.
```

`logs_0/` is completely empty — our container never started. Docker/harness infrastructure, auto-
excluded from win rates. This is why the manifest reads `status: failed`: `run.py:306` sets that
flag on *any* NO_RESULT game. 349/350 produced results; the run is sound.

No realtime timeouts, no lobby stalls, no game near the 1500s kill in 350 launches.

---

## Build verification (step 1)

- `git log` confirms `8d2fabd` (IA-311) and `d9f4355` (IA-281), merged at `6e32a9d` with no conflict
  on top of `aea84b3` release 0.63.
- `mvn -o clean package` → BUILD SUCCESS, **501 tests**, 0 failures. (Brief said 493; the tree has 8
  more. Not a defect, noted for the record.)
- `mvn -o checkstyle:check` → 0 violations.

## Artifacts

- `batch-results/20260904-031526/` — `dataset_games.csv` (350 rows), `dataset_engagements.csv` (6881 rows)
- `batch-results/20260903-210912-baseline/` — batch 18 recollected for the like-for-like comparison
  (345 rows / 5122 engagements)
