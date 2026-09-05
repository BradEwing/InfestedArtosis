# ZvP performance and the Tomas Cere regression — CLAUDE findings

Scope: read-only investigation of `6e32a9d` (IA-311 `8d2fabd` + IA-281 `d9f4355`) against release 0.63,
using batch `20260904-031526` vs baseline `20260903-210912`, the per-game
`write_0/telemetry_squad_decisions.csv` band telemetry, and `logs_0/unit_events.csv`. No source was
modified and no Maven run. One throwaway JBWAPI probe was compiled in the scratchpad to read exact
`UnitType` values rather than guess game mechanics; its output is quoted below.

---

## 1. Verdict

**Mistuned, not misguided — and mistuned in a specific, cheap-to-fix way: scope item 2 (the threshold
retune) was skipped, and the argument used to justify skipping it is wrong.** The sqrt-effective-HP
term moves the ZvP strength table *towards* physical reality, not away from it. On the old table one
Zealot priced at 1.16 Zerglings; on the new table it prices at 2.49. A frame-level duel model (5 dmg /
8 cd Zergling vs 100 HP + 60 shields / 1 armour / 8×2 dmg / 22 cd Zealot) puts the break-even at
**3 Zerglings per Zealot**, and the classical Lanchester value `sqrt(dps*EHP)` puts it at 2.31. The new
table is close to both; the old table was off by a factor of two in the other direction. The mirror is
untouched by construction and the batch confirms it empirically (ZvZ 53% vs 52%, mirror exchange 0.743
→ 0.957, enemy price ratio ×1.01). There is no implementation fault in the term itself. What shipped
broken is that the engage threshold 1.4 and the retreat threshold 0.8 were left on the old scale, so
the *effective* ZvP engage bar roughly doubled.

Confidence: **high** that the term should be kept (the mirror control, the duel model and the batch's
own exchange-by-ratio curve all agree). **Medium-high** on the specific constants below — they come
from two independent routes that happen to converge, but neither is a controlled experiment.

What would change my mind: a ZvP batch with IA-281 unchanged and the ZvP thresholds at 0.9 / 0.5 that
still fails to restore Cere above ~90% *and* still leaves the ex-Cere ZvP exchange rate at ~0.11. That
would mean the shape of the term, not its calibration, is misreading the matchup, and I would move to
the true Lanchester form `sqrt(dps * EHP)` (root on both halves) or revert.

---

## 2. H1: confirmed on the mechanism — but its arithmetic accounts for less than half of what the batch shows, and the "9x" it is asked to explain is a statistical artefact

### 2a. The arithmetic is right

JBWAPI 2.2.0 values, read from the jar rather than the ticket:

| unit | maxHP | shields | EHP | old g2g | new g2g | multiplier |
|---|---|---|---|---|---|---|
| Zergling | 35 | 0 | 35 | 1.8645 | 1.7441 | **0.935** |
| Zealot | 100 | 60 | 160 | 2.1696 | 4.3391 | **2.000** |
| Dragoon | 100 | 80 | 180 | 2.5808 | 5.4747 | **2.121** |
| Probe | 20 | 20 | 40 | 0.7223 | 0.7223 | 1.000 |
| Marine | 40 | 0 | 40 | 1.5485 | 1.5485 | 1.000 |

The brief's table reproduces exactly. Our side fell 6.5%, the Protoss army side doubled, so the ZvP
ratio scale fell by 0.468 and the unchanged 1.4 engage threshold now sits at **2.99 on the old scale**.
In Zergling terms, with the 0.75 no-Metabolic-Boost penalty applied, the sim used to demand
`1.4 × 2.1696 / 1.3984 = 2.17` Zerglings per Zealot to engage; it now demands
`1.4 × 4.3391 / 1.3081 = 4.64`. The true break-even is 3. **The old bar was slightly too aggressive;
the new bar is 55% too conservative.**

### 2b. Confirmed in the telemetry, at the predicted magnitude

Measured on Cere decision rows (median of `sim_enemy_strength / enemy_supply_believed_real`):

- enemy strength per believed supply: **0.747 (baseline) → 1.647 (this run), ×2.20**
- restricted to rows where *all* measured enemy strength sits inside 320 px, so IA-281 contributes
  nothing: **1.890, ×2.53**
- our strength per squad member: **1.2285 → 1.2320**, flat. (Predicted −6.5% for pure Zerglings; the
  gap is adjacent-squad strength, which lands in the numerator but not in `squad_size`. Either way,
  our side did not move and theirs did — exactly H1's claim.)
- same measurement for Zerg opponents, inner band only: **×1.01**. The mirror is untouched.
- for Terran opponents, inner band only: **×1.25** (Marine 1.00, SCV 1.22, Vulture 1.41, Tank 1.94,
  Bunker override unchanged). ZvT should have moved a quarter as far as ZvP, and ZvT win rate is flat
  (42.8% vs 41.7%).

Only the enemy side of the ZvP ratio inflated, the mirror is exactly 1.000, and that is why checking
our own units made the "thresholds still hold" argument look correct. **H1's diagnosis of the blind
spot is right.**

### 2c. But the "9x" is a median artefact, not a 9x change

`sim_ratio_open` medians of 19.36 → 2.13 come from the engagement-open subsample, which is bimodal:
one mode is real fights, the other is "first contact where the only measured enemy was a worker".

| Cere, measured engagement opens | baseline | this run |
|---|---|---|
| share below 1.4 | 17.5% | 38.1% |
| share 1.4–10 | 30.8% | 22.6% |
| **share ≥ 10** | **51.7%** | **39.3%** |
| median | 19.36 | 2.13 |
| median of the sub-10 mass | 2.578 | 0.904 |

The ≥10 mass fell from 51.7% to 39.3%; the median simply crossed the gap between the two modes. On the
population that actually matters — every sim decision with a measured enemy — the move is
**1.510 → 0.666, a factor of 2.27**, against H1's predicted 2.14. **H1 explains the real collapse
almost exactly. There is no unexplained 9x to account for.**

### 2d. Attribution between the two changes — the band telemetry does separate the inputs

For Cere, on the 5,064 measured decision rows of this run, using `sim_enemy_strength_0_320` as the
"IA-281 not present" counterfactual denominator. That substitution is exact: for every non-positional
type the engagement radius is 320, so the new `distanceWeight(min(d,320))` equals the old
`distanceWeight(d)` on those rows.

| verdict mix, same rows | RETREAT | ADVANCE | ENGAGE |
|---|---|---|---|
| as shipped (both changes) | **68.7%** | 17.1% | 14.2% |
| IA-281 removed (inner band only) | 46.3% | 39.9% | 13.8% |
| both removed (enemy de-inflated ×1.6, mixed Probe/Zealot) | 37.9% | 39.9% | 22.1% |
| both removed (enemy de-inflated ×2.14, pure Zealot) | 27.0% | 39.9% | 33.0% |
| baseline batch, actual | 22.9% | 52.7% | 24.4% |

Broken out on the RETREAT verdicts themselves (n = 4,195):

- **944 (22.5%) had zero enemy strength inside 320 px.** The squad retreated from units 320–640 px away
  with nothing in contact. Under the old code these were ADVANCE. **100% IA-281.**
- of the 3,251 with something inside 320 px, **430 flip to ENGAGE** when the outer bands are removed.
  **IA-281, via the ratio.**
- removing IA-311's enemy inflation on top flips a further **510 (×1.6 estimate) to 1,180 (×2.14)**.

**IA-281 is responsible for roughly 1,374 of the extra RETREATs (33%), IA-311 for roughly 510–1,180
(12–28%). Both are material; for Cere, IA-281 is the larger single contributor.** That is a correction
to the brief's framing, which treats H1 as the primary target: H1 is real, but it is the smaller half.

### 2e. The threshold that restores the old ZvP engage behaviour

Two independent routes:

- **Analytic:** `1.4 × 0.468 = 0.655` reproduces the old vs-Zealot bar exactly.
- **Empirical:** on this run's Cere rows, an engage threshold of **0.655 gives a 50.9% ENGAGE share
  among measured decisions**, against the baseline's actual 54.9%. The two routes agree to within four
  points without being fitted to each other.

But restoring the old behaviour restores a bar that was itself wrong (2.17 lings per Zealot against a
true 3). The **calibrated** threshold is higher — see §5.

---

## 3. The Cere mechanism

Cere is a clean experiment: 4Pool in 50/50 and 57/57 games, opponent always Protoss, Probes plus about
8.9 Zealots per game, no Photon Cannons. 98% (56-1) → 80% (40-10). Fisher exact **p = 0.0026**; this is
not the n=50 noise floor.

**The 40 wins are indistinguishable from the baseline's wins.** Probes killed by frame 6000: 6.15 vs
6.16. By frame 8000: 15.55 vs 16.32. Lings built: 21.8 vs 21.6. Median end frame 8,838 vs 9,149. When
the 4Pool converts, nothing changed.

**The 10 losses are a distinct failure that does not exist in the baseline.**

| by frame 8000 | this run, wins | this run, losses | baseline, wins |
|---|---|---|---|
| Probes killed | 15.6 | **3.3** | 16.3 |
| Zealots killed | 4.2 | **0.8** | 4.3 |
| Zerglings lost | 9.8 | **18.3** | 10.5 |
| Zerglings built | 30.9 | 31.4 | 31.5 |

Same army, double the losses, a fifth of the kills. This is a combat-behaviour failure at frames
4,000–8,000, not an economic one.

**Where the Zerglings die.** Assigning each Zergling death before frame 12,000 to the most recent
squad status:

| | FIGHT | RETREAT | CONTAIN | RALLY |
|---|---|---|---|---|
| this run, wins (n=438) | 65.5% | 16.2% | 1.4% | 16.9% |
| **this run, losses (n=379)** | 35.9% | **42.0%** | **11.9%** | 10.3% |
| baseline, wins (n=611) | 59.2% | 18.0% | 0.3% | 22.4% |

In the losing games **54% of Zerglings die while the squad is not fighting**, against 18% in the winning
ones. They die without trading. (Caveat: status is taken from the most recent `STATUS_CHANGE` across the
game's squads, not matched to the dying unit's own squad. Directional.)

**Why they are not fighting — the containment flap.** `SquadManager.simulateFightSquad`, `case RETREAT`,
calls `tryEnterContainment(squad)` *before* falling back to a retreat (`SquadManager.java:894-901`). The
very next frame `evaluateContainingSquad` (`SquadManager.java:1051-1058`) sees enemies inside
`ENEMY_DETECTION_RADIUS` (512) and immediately sets the squad back to FIGHT with a fresh 72-frame fight
lock. Each RETREAT verdict issued while an enemy is within 512 px therefore costs the squad one frame of
arc-move orders plus a full target reassignment, and buys nothing.

| Cere | baseline | this run |
|---|---|---|
| CONTAIN entries per game | 1.5 | **54.0** |
| CONTAIN episodes lasting ≤ 2 frames | 31.8% | **96.1%** |
| CONTAIN → FIGHT transitions, total | 84 | **2,701** |
| median FIGHT dwell between status changes | 93f | **1f** |
| RETREAT frames per game | 643 | **3,188** |

Tripling the RETREAT verdict rate multiplied this churn by 36. The path existed before (VOID already
showed 385 CONTAIN → FIGHT transitions per game on the baseline); against Cere it went from
non-existent to 54 per game.

**A retreat also costs 5 seconds unconditionally.** `retreatHysteresis` is `Time(0,5)` = 120 frames, and
`simulateFightSquad` returns early on `status == RETREAT && retreatLocked` with no escape hatch, however
good the ratio becomes. Median RETREAT dwell is exactly 120 frames in both batches; the number of
episodes went 4.2 → 16.1 per game.

**And the fight lock now breaks four times as often.** `fightLockHolds` releases a FIGHT when a measured
RETREAT falls below the *retreat* threshold (0.8 for Protoss). That threshold was also left on the old
scale. Share of measured Cere decisions below 0.8: **21.3% (baseline) → 65.4% (this run)**. The one
mechanism designed to keep a committed squad committed was disarmed by the same rescaling.

**What finishes the loss games (not what starts them).** In the 10 losses, 6.3% of decision rows report
enemy strength above 100, maximum 724. Those are Protoss Scarabs: `UnitStrength` prices a Scarab at
**335.3** (387.1 before IA-311), 192 Zerglings, while a Reaver prices at **0** because
`UnitType.Protoss_Reaver.groundWeapon()` is None. Cere builds 0.48 Reavers and 4.24 Scarabs per game.
Once a Scarab exists the ratio collapses to about 0.03 and the squad can never leave RETREAT. **This is
pre-existing** — the baseline's single loss shows the same signature (28.8% of its rows above 100) — and
the first such row appears at frames 11,235–14,529, long after the divergence at 4,000–8,000. It seals
the losses; it does not cause them. No winning game contains one.

---

## 4. What else I found

### 4a. What H1 does not explain: Cere fell 18 points, non-Cere ZvP did not move at all

| ZvP, by race actually played (derived from `logs_1/unit_events.csv`) | baseline | this run |
|---|---|---|
| Tomas Cere | 56-1 (98%) | 40-10 (80%) |
| Dave Churchill | 0-12 | 1-17 |
| MadMixR | 3-10 | 2-16 |
| WillBot | 0-14 | 0-15 |
| **non-Cere ZvP** | **3-36 (7.7%)** | **3-48 (5.9%)** — Fisher p = 1.00 |

They did not move together because **the engage threshold is the binding constraint in exactly one of
the two regimes.** Against Cere the bot already has an army that wins by committing, so a bar that
blocks committing can only cost games. In the other 51 ZvP games the bot loses on production and
composition: median 20,555 frames, 109 of our units lost against 14 of theirs, peak supply 57. You
cannot recover a 1:8 army-value deficit with a better engage decision, and the ex-Cere ZvP exchange rate
(0.108 vs 0.162, overlapping intervals, both terrible) says so. **IA-311 was aimed at a problem the
combat sim does not own.** That is the most important strategic finding here: the ZvP acceptance
criterion on the ticket was never achievable by a strength-table change.

### 4b. The Lanchester justification in the javadoc does not support the implemented form

The javadoc is right about the law: with `dN_a/dt = -N_b·d_b/h_a`, the conserved quantity is
`(d_a/h_b)N_a² − (d_b/h_a)N_b²`, so parity is `N_a·sqrt(d_a·h_a) = N_b·sqrt(d_b·h_b)` — the sums of
`sqrt(dps · EHP)` match. But that is the square root of **both** halves. The implementation ships
`dps · sqrt(EHP)`, which is not a Lanchester strength, and the stated reason for the asymmetry ("every
correction the simulator layers on top is linear in damage") is really an argument that the table is
*not* a Lanchester strength — in which case the root on the HP half has no derivation either. The form
is a hybrid.

It is nonetheless defensible on results, because BW ground units vary far more in EHP than in DPS:

| Zerglings per Zealot at parity | old table | shipped form | true Lanchester | frame-level duel |
|---|---|---|---|---|
| | 1.16 | **2.49** | 2.31 | **3 (kills it, loses 2)** |

The forms diverge where DPS varies a lot: Firebat vs Marine is 2.50 under the shipped form and 1.67
under Lanchester. **Keep the shipped form, do not switch to full Lanchester on this evidence, and delete
the Lanchester paragraph from the javadoc — it justifies a formula that was not written.**

### 4c. `EHP_REFERENCE = 40.0` is a no-op for every verdict except through the five overrides

Every entry produced by the static-block loop is multiplied by `sqrt(EHP)/sqrt(40)`. The `1/sqrt(40)` is
common to all of them and cancels exactly in any ratio of loop-computed strengths. The constant changes
**no** engage decision between mobile units. Its only effect is on the five hardcoded overrides at
`UnitStrength.java:52-56`, which are written after the loop and never see the factor. Choosing 40 pinned
the overrides against Zergling / Drone / Probe / Marine — the exact subset whose multiplier is ~1.0 —
which is why `staticDefenseOverridesKeepTheirZerglingEquivalence` passes, and why it proves nothing. The
overrides' price against everything else moved:

| | vs Zergling | vs Zealot (before) | vs Zealot (after) |
|---|---|---|---|
| Sunken Colony (6) | 3.22 → 3.44 | 2.77 | **1.38** |
| Photon Cannon (6) | 3.22 → 3.44 | 2.77 | **1.38** |

Loop-consistent values would be Sunken 14.64 and Cannon 8.69. **Our static defence is now worth half
what it was against a Protoss army, and enemy Cannons are under-priced by the same factor, which will
make the bot walk into cannon walls.** No opponent on this bench builds Cannons (Cere makes none), so
the batch cannot show the harm — but it is a live inconsistency that scope item 5's "leave them alone
unless the retune requires it" should have caught: the retune did require it.

A **supply-normalised** form would be worse, not better. EHP per supply is 80 for a Zealot and 70 for a
Zergling — nearly equal — so normalising by supply deletes the durability term almost entirely and
returns the table to its old, wrong answer.

### 4d. Shields and armour

Shields at face value is, if anything, *conservative* against Zerglings. A Zealot's 1 armour applies only
to hit points and reduces a Zergling's 5 damage to 4, so its 100 HP behave like 125 against Zerglings
while its 60 shields take full damage: 185 Zergling-equivalent HP against the 160 the table uses. Shield
regeneration (7/256 per frame) is negligible over a 74-frame duel. **Adding armour would make the Zealot
stronger and deepen the current error. Do not add it until the thresholds are fixed.** Note the
asymmetry that already exists: `armorUpgradeCorrection` and `attackUpgradeCorrection` are applied only to
our own units, never to the enemy side.

### 4e. IA-281 defects

- `enemyDistanceWeight` assumes every non-positional enemy inside 640 px is charging at top speed:
  `framesToContact = (dist − radius) / type.topSpeed()`. A Zealot standing still in its own mineral line
  600 px away is weighted `0.875 × (1 − 80/240) = 0.583`. There is no test that the enemy is approaching,
  or even that it intends to. This is what produces the 944 RETREAT verdicts with nothing inside 320 px.
- `OUTER_HORIZON_RADIUS` (640) exceeds `SquadManager.ENEMY_DETECTION_RADIUS` (512), which is the gate
  deciding whether a squad simulates at all and whether a containing squad breaks out. The sim now
  reasons about a band the rest of the manager cannot see; that is the direct cause of the CONTAIN/FIGHT
  flap in §3.
- `NEARBY_THREAT_RADIUS` silently moved 512 → 832 (`OUTER + 192`), widening `threatBeyondRadius` and
  therefore `blindAdvanceHeld`, with no separate justification.

### 4f. The tests would not have caught H1 — one of them blesses it

`UnitStrengthTest` measures six of its seven assertions against Zerglings, the side that did not move.
`zerglingMirrorRatiosAndVerdictsAreUnchanged` sweeps 12×12×3 thresholds through `selectResult` in the
*mirror*, where the multiplier is exactly 1.000 by construction — it cannot fail.
`marineToZerglingRatioBarelyMoves` covers ZvT, where the multiplier is also 1.000. There is no assertion
anywhere that a ZvP force ratio at the shipping threshold is unchanged or correct.

`threeZerglingsNoLongerEngageALoneZealot` asserts RETREAT at 3v1 and ENGAGE at 4v1 — and **3 Zerglings
beat 1 Zealot**, killing it for the loss of 2, a 2:1 mineral trade. The test encodes the regression as
the intended behaviour.

### 4g. Lurker 2.5 / Mutalisk 1.5 (scope item 4)

Dropping the Ultralisk 2.0 was correct — the EHP factor gives it 3.162 on its own. The remaining two now
stack: Lurker `2.5 × 1.768 = 4.42×` its damage-only value, Mutalisk `1.5 × 1.732 = 2.60×`. Both
multipliers nominally price damage *shape* (splash, bounce) rather than durability, so they are not
prima facie double-counting — but both were fitted when no durability term existed, so part of their
magnitude was standing in for exactly what the term now supplies. **This batch cannot settle it**: there
is no ZvP Lurker or Mutalisk engagement volume. Leave them, re-measure after the thresholds are fixed.

---

## 5. Recommendation, in priority order

**1. Retune the ZvP thresholds. `HorizonCombatSimulator.java:552-568`: engage 1.4 → 0.9, retreat
0.8 → 0.5.**

Evidence, two independent routes converging:

- **Physics.** At 0.9 the sim demands `0.9 × 4.3391 / 1.3081 = 2.99` un-speeded Zerglings per Zealot. The
  frame-level duel model puts the break-even at exactly 3 (3 lings kill the Zealot and lose 2, a 2:1
  mineral trade; 2 lings lose outright). Against Dragoons it demands 3.77.
- **This batch's own outcomes.** Cere engagement exchange rate (enemy supply killed / our supply lost) by
  `sim_ratio_open` bucket on the new scale: 0.19 below 0.5, 0.80 at 0.5–0.8, **1.25 at 0.8–1.0**, 0.96 at
  1.0–1.2, 0.98 at 1.2–1.4, 1.34 at 1.4–1.8. **Break-even sits at ratio ≈0.8–1.0, not 1.4.** The bot was
  declining fights it wins.
- Retreat 0.5 keeps the engage:retreat proportion the ticket shipped (0.8/1.4 = 0.571) and restores
  `fightLockHolds` towards its old release rate (measured: 21.3% baseline, 65.4% at the current 0.8,
  37.8% at 0.5).
- For the record: **0.655** is the value that restores the *old* ZvP behaviour exactly (`1.4 × 0.468`,
  and independently the value reproducing the baseline's 54.9% ENGAGE share on this run's rows). I
  recommend 0.9 instead because the old behaviour was itself about 30% too aggressive against the
  measured break-even.

Cheapest experiment: 50 games vs Tomas Cere only, one arm at 0.9/0.5, one arm at 0.655/0.37, both with
IA-281 unchanged. About 25 minutes at `--jobs 7`. Success is Cere back above 92% with the Zerg threshold
untouched. This single opponent is the highest signal per game on the bench — p = 0.0026 on the
regression, and the opener is bandit-locked to 4Pool so there is no strategy confound.

**2. Leave the Zerg threshold (1.3) alone. Move the Terran threshold 1.4 → 1.25.** The mirror measured
×1.01 and ZvZ is flat, so do not touch it. Terran measured ×1.25 on the inner band, so the ZvT bar rose
by a quarter. ZvT win rate is flat (42.8% vs 41.7%), so this is a correction, not a fix; lower priority
and weaker evidence (the Bunker override and composition confound the ×1.25). Ship it only after item 1's
Cere test passes, so the two are not confounded.

**3. Bring the five static-defence overrides onto the new scale** (`UnitStrength.java:52-56`), by
multiplying each by its own EHP factor: Sunken 6 → 16.4, Photon Cannon 6 → 13.4, Bunker 12 → 35.5, Spore
2 → 6.3, Missile Turret 2 → 4.5. They are currently half-priced against every Protoss and Terran mobile
unit. Evidence: §4c. This bench cannot test it — no opponent builds Cannons and only Ecgberht/VOID build
Bunkers — so the cheapest experiment is defensive: 50 games vs VOID (Terran, 62% now) checking that the
Bunker rescale does not make us refuse to break bunkers. Do **not** ship this in the same arm as item 1.

**4. Stop entering containment on a RETREAT verdict when enemies are inside `ENEMY_DETECTION_RADIUS`**
(`SquadManager.java:894`). 96.1% of the 2,701 containment entries vs Cere lasted a single frame, because
`evaluateContainingSquad` reverses them immediately. The guard is one line and costs nothing:
`tryEnterContainment` should be skipped when `!enemyUnitsNearSquad(squad).isEmpty()`. This is a
pre-existing bug (VOID showed 385 flaps per game on the baseline) that IA-281 + IA-311 amplified 36× against
Cere. File separately from IA-311.

**5. Gate IA-281's outer bands on the enemy actually approaching, or cap `OUTER_HORIZON_RADIUS` at 512.**
Right now a stationary Zealot 600 px away is priced as if it were charging, and the sim reasons about a
band `SquadManager` cannot see. 22.5% of this run's Cere RETREAT verdicts had zero enemy strength inside
320 px. The cheap version is the cap (one constant, restores the invariant
`OUTER_HORIZON_RADIUS ≤ ENEMY_DETECTION_RADIUS`); the correct version compares `ObservedUnit` positions
across frames and drops the contact term for enemies whose distance to the squad centre is not falling.

**6. Fix or delete `threeZerglingsNoLongerEngageALoneZealot`.** It asserts the regression. Replace the
Zergling-only assertions with at least one that pins a ZvP force ratio against the shipping threshold, so
the next scale change cannot pass CI while doubling the engage bar.

**7. Price Reaver/Scarab.** `UnitStrength` gives the Reaver 0 and the Scarab 335. Pre-existing, already
tracked in project memory, and it makes every Cere loss unrecoverable once a Scarab exists. Out of scope
for IA-311, but it is the reason the losses run to 17,000 frames instead of ending.

**8. Delete the Lanchester paragraph from `effectiveHitPointFactor`'s javadoc.** The law it cites implies
`sqrt(dps · EHP)`; the code implements `dps · sqrt(EHP)`. Keep the code, drop the false derivation, and
say instead that the form is an empirical compromise.

---

## 6. What I could not determine

- **The batch cannot attribute *outcomes* to either ticket.** The band telemetry separates the sim's
  *inputs* cleanly (§2d) because `sim_enemy_strength_0_320` is computed with weighting identical to the
  old code for every non-positional type. It says nothing about which change cost the 18 points. Only a
  single-change batch can. Given the input decomposition puts IA-281 ahead of IA-311 for Cere, **I would
  run IA-281-alone first if only one arm is affordable.**
- **The de-inflation factor for IA-311 is a bracket, not a measurement** (×1.07 all-Probe to ×2.14
  all-Zealot). Enemy composition inside the sim's radius is not logged per frame; only aggregate believed
  supply is. Everything in §2d that removes IA-311 is therefore a range.
- **Every cross-batch distribution comparison is biased by an endogenous row population.** Decision rows
  are emitted on status change, and the new code changes status 3.4× more often per Cere game (122 vs 36
  rows). More retreats produce more rows, which shifts the very distributions used to measure the
  retreats. Within-run counterfactuals (§2d) do not have this problem; cross-run medians (§2b, §2c) do,
  in an unknown direction.
- **The FIGHT-vs-RETREAT exchange comparison at matched ratio is observational.** At `sim_ratio_open` in
  [0.8, 1.4) this run's Cere engagements exchanged at 2.67 when they closed in FIGHT and 0.28 when they
  closed in RETREAT (n = 16 and 25). The gap is large and points the way item 1 predicts, but which
  squads ended in FIGHT was decided by lock state and by how the fight developed, not randomly. I did
  **not** use it as evidence for the 0.9 threshold; the two routes in §5 item 1 are independent of it.
- **I cannot show the containment flap costs games.** I can show it is new against Cere (1.5 → 54 entries
  per game), that 96% of entries last one frame, and that each re-issues orders to every unit twice.
  There is no per-unit order or attack telemetry, so whether that translates into lost damage is
  inference.
- **Whether the Lurker 2.5 and Mutalisk 1.5 multipliers double-count.** No ZvP Lurker or Mutalisk
  engagement volume in this bench. Unresolvable here.
- **I did not open the `.rep` replays**, only `bot.log`, `frames.csv`, `unit_events.csv` and the telemetry
  CSVs. Claims about what units did on screen are inferred from event and decision logs.
- **Noise floor.** At n=50 the Wilson interval is about ±13 points, and the brief's caution about 4-point
  drift on dice and learning files alone applies. MadMixR −11 and liongis −11 are not results and I have
  not treated them as any. **Only Tomas Cere clears** (Fisher p = 0.0026). Non-Cere ZvP moved 7.7% → 5.9%,
  Fisher p = 1.00 — genuinely nothing, in either direction.
- **The baseline batch is not a perfect control**: 345 games with an uneven opponent split (Cere 58,
  liongis 63, VOID 36) against this run's clean 7×50, in accumulate mode, so the learning files differ.
  The Cere comparison is where this matters least — the bandit played 4Pool in 50/50 and 57/57.
