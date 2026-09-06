# Basket-12 batch — IA-313 + IA-314 after reviewer pass

Jar `c0e5410` (merge of `b53b5df` IA-313 and `34002f1` IA-314 onto `a61cffd`).
600 games, 12 opponents x 50, `--jobs 12`, accumulate, learning wiped, 2026-09-05.
594 games kept after the >=25-minute cull. `mvn -o clean package` 495 tests green, checkstyle 0.

Comparator is the **BASIL ladder, last 6 months** (2026-03-09..2026-09-06, 3,877 games, overall
41.7%). This basket was built to track ladder win rate, so the ladder rate is the reference, not the
earlier 8-bot bench.

Claims are VERIFIED (recomputed from manifests and per-game telemetry) unless marked INFERRED.
Twelve Pi agents analysed one opponent each; their per-opponent reports are the source for the
mechanism findings, and I verified the two most actionable claims myself — one confirmed, one only
half-confirmed. Both are flagged below.

## Headline

| opponent         | local W-L | local% | BASIL W-L | BASIL% | delta | Fisher p | sig |
|------------------|-----------|--------|-----------|--------|-------|----------|-----|
| BananaBrain      |      2-48 |     4% |      0-18 |     0% |    +4 | 1.00e+00 |     |
| GuiBot           |      50-0 |   100% |     16-50 |    24% |   +76 | 4.27e-19 | *** |
| Lukas Moravec    |     39-11 |    78% |     26-30 |    46% |   +32 | 1.28e-03 | *** |
| Tomas Cere       |     39-11 |    78% |     51-14 |    78% |    -0 | 1.00e+00 |     |
| MisHanBot        |      8-42 |    16% |      2-31 |     6% |   +10 | 3.02e-01 |     |
| MadMixT          |     12-34 |    26% |     28-38 |    42% |   -16 | 1.08e-01 |     |
| Matej Istenik    |      49-1 |    98% |     34-24 |    59% |   +39 | 3.90e-07 | *** |
| Sungguk Cha      |      48-2 |    96% |     51-9 |     85% |   +11 | 6.41e-02 |     |
| ZurZurZur        |      9-41 |    18% |     12-39 |    24% |    -6 | 6.25e-01 |     |
| Zerg Hell        |     15-35 |    30% |     21-50 |    30% |    +0 | 1.00e+00 |     |
| Pineapple Cactus |      41-9 |    82% |     63-18 |    78% |    +4 | 6.59e-01 |     |
| MicRobot         |     11-37 |    23% |     11-47 |    19% |    +4 | 6.38e-01 |     |
| **TOTAL**        | **323-271** | **54.4%** | **315-368** | **46.1%** | **+8.3** | 3.5e-03 | |

`***` survives Bonferroni for 12 tests (p < 0.0042). No opponent is significantly **worse** than
ladder — MadMixT's -16 is p=0.11.

### The +8.3 is an artifact of one broken opponent

**GuiBot does not play.** In all 50 games it completes exactly **4 Probes and 1 Nexus** and nothing
else — no Pylon, no Gateway, no army, 0 minerals spent, supply frozen at 8, and its `bot.log` shows
a Wine `No server proc ID` attach loop. All 50 games last 7.08-8.57 minutes, the time to walk over
and kill an idle Nexus. The 50-0 is not a measurement.

| basket | local | BASIL | delta | p |
|---|---|---|---|---|
| all 12 | 54.4% [50.4, 58.3] | 46.1% | +8.3 | 0.0035 |
| **excluding GuiBot** | **50.2% [46.0, 54.4]** | **48.5%** | **+1.7** | **0.60** |

**Against the ladder this jar is at parity, not ahead.** That is the honest headline.

Two caveats in the other direction, both genuine:

- **Matej Istenik (+39) and Lukas Moravec (+32) are real games,** not broken installs. I suspected
  during the run that Matej was the same artifact as GuiBot; it is not — it completes 41.2 units per
  game including 88 sieged tanks across the batch, and Moravec fields 726 Zealots and 225 Scarabs.
  Both deltas survive correction and are attributable to the converged all-in, though a fixed local
  opponent snapshot with a fresh bandit is a friendlier test than the ladder.
- **Sungguk Cha's +11 is probably a map-pool artifact** (INFERRED, agent finding): locally 10-2 on
  2-player maps and 38-0 on 3p/4p; the ladder's 85% matches our 2p rate of 83%.

## IA-313: fixed, and more completely than the previous jar

`REACTION_EARLY_RUSH_DRONE` cancels per game, all 594 games:

| metric | pre-fix (8-bot bench, `e5ed047`) | first merge (`b787374`) | **this jar (`c0e5410`)** |
|---|---|---|---|
| mean | 563.7 | 2.9 | **0.4** |
| median | 7.0 | 0.0 | **0.0** |
| max | 3525 | 68 | **14** |
| games >50 cancels | 19/48 (40%) | 1/50 (2%) | **0/594 (0%)** |

Not one game in 594 exceeded 50 cancels; the worst was 14. The reviewer's 3-second stand-down
debounce removed the residual tail the first merge still had. Every one of the 12 agents independently
confirmed the reaction firing in single-frame bursts rather than per-frame.

**One caveat that cuts against reading this as pure success:** in several matchups the count is zero
because the reaction *never engaged*, not because it engaged cleanly. The Tomas Cere agent found 0
cancels in 50/50 games and attributes it to a detection gap rather than a livelock fix. The livelock
is definitively gone; the trigger's coverage is a separate open question.

## IA-314: works where it can act, but is untestable in most of this basket

The cap is on `ProtossBase`, so only the four Protoss opponents can exercise it.

| opponent | n | zerglings/g | hydras/g | mutas/g | Lair | Spire |
|---|---|---|---|---|---|---|
| Tomas Cere | 50 | 32.6 | 7.06 | 5.04 | 50/50 | 46/50 |
| BananaBrain | 50 | 69.6 | 15.14 | 2.10 | 21/50 | 11/50 |
| GuiBot | 50 | 0.0 | 7.90 | 0.00 | 12/50 | 0/50 (void) |
| Lukas Moravec | 50 | 80.8 | **0.00** | **0.00** | **0/50** | **0/50** |

- **Tomas Cere and BananaBrain: working as intended.** Tech flows alongside zerglings — Lair in
  50/50 and Spire in 46/50 vs Cere. This is the outcome IA-314 was written for, and it is a clear
  improvement on the previous jar's muta regression.
- **Lukas Moravec: zero tech in 50 games.** Not a cap failure — the agent found *no tech plan was
  ever enqueued*, so there was nothing for the cap to un-crowd. We still win 78%. IA-314 is
  unobservable here.
- The other 8 opponents are Terran or Zerg, where the method does not run.

## The dominant finding: the all-in builds have no second act

Seven of twelve agents independently reached the same conclusion from different opponents. This was
not in either ticket's scope and is the largest effect in the batch.

`SpeedlingAllIn` and `1HatchSpire` queue **zero** tech in the games where they stall, and the stall
is decided by one variable — whether the opponent lives long enough to reach air or splash:

| opponent | separator | wins | losses |
|---|---|---|---|
| Pineapple Cactus | enemy Mutalisks completed | 0/41 | 9/9 |
| ZurZurZur | enemy Mutalisks completed | <=3 (7 of 9 zero) | 6-22 in 41/41 |
| Zerg Hell | enemy Hydralisks completed | mean 1.2 | mean 66.2 |
| BananaBrain | enemy Corsairs completed | 0 in both wins | 48/50 games |
| Lukas Moravec | game length | median 8.0 min | 1W-8L past 10 min |
| MicRobot | game length | all Terran wins <=9.3 min | grinds to 12.7-19.6 min |

Against Pineapple Cactus and ZurZurZur the bot fields **zero anti-air units** while enemy mutalisks
kill it, with a median 5.4 minutes of warning after the first muta is *observed*. Against BananaBrain,
Corsairs destroy **696 of 738 Overlords built (94%)** and 41/50 games end supply-capped at <=16.

The convergent fix all seven propose is the same: **a stall/threat trigger out of the all-in into a
Hydralisk Den**, keyed on observed enemy air tech or a time gate (~8-9 min with the enemy main
alive). The Den is pool-tier tech, so it fits the hatchery-only identity of these builds.

## Verified concrete defects

I checked the two most actionable agent claims against the code and telemetry myself.

1. **CONFIRMED — Lurkers are larva-gated but consume no larva.**
   `ProductionManager.scheduleUnitItem` (`src/main/java/macro/ProductionManager.java:1029`) applies
   `canScheduleLarva` to every UNIT plan before any per-unit branching. `Zerg_Lurker` morphs from a
   Hydralisk and needs no larva, so lurker plans sit `NO_LARVA` with the tech and hydras already on
   the field. Impact vs MisHanBot: `>=9` lurkers completed is **5-1**, zero lurkers is **0-20**
   (n=30). Small, high-confidence fix.

2. **HALF-CONFIRMED — "SpeedlingAllIn without speed."** The outcome is real: Metabolic Boost
   completed in only **15/50** `SpeedlingAllIn` games vs Pineapple Cactus. But the proposed
   mechanism is wrong — I found **zero** Extractor CANCEL events — and it is not general: ZurZurZur
   got the upgrade in **42/42** and still lost 41. Worth a ticket for the missing upgrade, but it is
   not the reason we lose those matchups.

Other agent-reported defects, **not independently verified by me**:
`cancelImpossibleScheduledLurkerPlans` cancelling all rather than excess lurker plans (measured
2,779 cancels across 28 games, up to 1,551 in one); `OneHatchSpire.java:116` hard-capping mutalisks
at 11 in ZvZ; `ThreeHatchLurker` gating hydralisk demand on *floating* minerals so it collapses in
exactly the games that never float.

## Recommendations

1. **Ship IA-313.** Fully meets its criteria, zero livelock in 594 games, no matchup regressed.
2. **Ship IA-314.** It behaves correctly where it can act, and the muta regression seen in the
   previous jar did not reproduce here (Cere: Spire 46/50, 5.04 mutas/game).
3. **Fix the GuiBot install and quarantine its learning rows before the next batch.** The 50 fake
   wins in `GuiBot_Protoss.csv` all reward 12Pool/3HatchHydra and will bias the D-UCB bandit toward
   that arm against the real ladder GuiBot, which beats us 3 games in 4.
4. **Open a ticket for the all-in transition.** This is worth more than either shipped ticket:
   six opponents, ~150 losses, one mechanism, and a warning window already visible in telemetry.
5. **Fix the lurker larva gate** — small, verified, and it gates the win condition in ZvT.

## Method notes

- Runs `20260905-200916` (600 launched, `status=completed`, no Docker aborts) plus a short top-up
  for 6 games. All 12 opponents confirmed launching by container inspection.
- Culled >=25 min: MadMixT 5, MicRobot 3, Lukas Moravec 2, Tomas Cere 1. Non-WIN/LOSS: 6 total.
- **Slow opponents' partial records are untrustworthy mid-run.** MadMixT read 0-15 at 33% completion
  and finished 12-34; its record after the bandit converged was 12-19 (38.7%), consistent with its
  42.4% ladder rate. Mid-batch per-opponent numbers should not be quoted.
- Per-opponent agent reports and data packs: `scratchpad/pi/REPORT_*.md`, `pack_*.json`.
