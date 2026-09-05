# ZvP / IA-311 investigation — PI

Investigator: Pi. Batch under test: `20260904-031526` (`6e32a9d`, IA-311 + IA-281) vs baseline
`20260903-210912` (`6d09bf8`). All per-frame evidence below comes from
`telemetry_squad_decisions.csv` under `C:\Users\bradl\AppData\Roaming\scbw\games\GAME_<name>\write_0\`
(the per-band telemetry IA-281 added), read via `/mnt/c/...`.

---

## 1. Verdict

**Mistuned, not misguided — with one important refinement: tuning the threshold alone is necessary
but not sufficient.** The effective-HP term is directionally correct (a Zealot genuinely out-trades
Zerglings per unit; the ZvZ canary is flat because the term cancels exactly in the mirror, which is
by construction and is confirmed at 10-9 vs 13-12). The defect is that scope item 2 — retuning the
engage/retreat thresholds — was skipped, so in ZvP only the enemy side of the ratio inflated and the
Protoss engage bar moved from 1.4 to an effective ~3.0 on the old scale. My counterfactual says a
Protoss engage threshold of **0.65** and retreat threshold of **0.37** restores baseline verdicts on
Tomas Cere games to 83% agreement with IA-281 left alone, and to **99.7%** if IA-281's beyond-horizon
contribution is also pulled back. Confidence: high on the mechanism (arithmetic + per-band
telemetry), moderate on the constants (they are fitted on Cere's Zealot-dominated games; DC ZvP
suggests 0.60). **What would change my mind:** a 50-game Cere batch at 0.65/0.37 that does not
recover to ≥90% wins. That would mean the re-priced table is wrong at the one margin that matters —
3-4 Zerglings vs 1-2 Zealots among probes — and the term, not the threshold, is the problem.

## 2. H1 — confirmed on mechanism, with a corrected attribution of magnitude

**Confirmed.** The per-unit arithmetic is exact and composition-independent: the EHP factor is
`sqrt((maxHP+maxShields)/40)`, so Zealot ×2.000, Dragoon ×2.121, Marine ×1.000, Zergling ×0.9354,
Hydralisk ×1.4142. Our ZvP early army is Zerglings; Cere's is Zealots (verified: 23-25 Zealots, 5-6
Dragoons in loss game KU4HQ075). The ratio scale is therefore 0.9354/2.000 = **0.468** vs Zealots
(0.441 vs Dragoons), and exactly **1.000** in the ZvZ mirror — which is why the implementation's own
"Zergling only moved 1.865 → 1.744" check looked fine and why the mirror canary stayed clean. The
unchanged 1.4 threshold is an effective **2.99** (Zealot) / **3.17** (Dragoon) on the old scale.

**Magnitude, separated from IA-281 using the band telemetry.** Across all 5,074 ENGAGE/RETREAT
decision rows in the 50 Cere games, 4,205 were RETREAT. Reconstructing the baseline-code ratio per
row (`our / inner_band`, since the old horizon counted only enemies within 320px, and the inner band
uses the identical distance weighting) and applying the 0.468 scale:

| class of RETREAT row | n | share | share before frame 6000 |
|---|---|---|---|
| A. enemy entirely beyond 320px — **pure IA-281** (baseline code: unmeasured → ADVANCE) | 954 | 22.7% | 27% |
| B. baseline would also have retreated (ratio < 1.4 on old scale) | 1,641 | 39.0% | 11% |
| C. **flipped by the IA-311 scale** (old-scale ratio ≥ 1.4) | 1,610 | 38.3% | **62%** |

In the early window (< frame 6000) that decides 4Pool games, IA-311's scale explains ~62% of retreat
verdicts, IA-281's horizon ~27%, and only ~11% would have retreated anyway. **IA-311 is the primary
driver exactly where Cere games are decided.**

**The 9× median collapse (19.36 → 2.13) is class migration, not a 9× scale shift.** The ENGAGE-row
median did not fall at all (41.5 → 56.7); RETREAT rows went 41 → 165 and dragged the combined median
down. H1's per-unit prediction (~2.1×) is correct; the median statistic mixes classes.

**Restore constants.** Counterfactual verdict agreement against baseline-code verdicts on the same
rows (baseline code retreats on 1,641 of 5,074):

| configuration | t=1.4 | t=0.65 |
|---|---|---|
| as shipped | 4,195 retreats / 49.7% agree | 2,463 / 83.4% |
| middle+outer bands halved | 3,596 / 61.5% | 2,051 / 91.5% |
| IA-281 horizon contribution removed | 2,821 / 76.7% | **1,628 / 99.7%** |

So: **engageThreshold(Protoss) 1.4 → 0.65** (fitted 0.66 on Cere, 0.60 on DC's Zealot/Dragoon mix),
**retreatThreshold(Protoss) 0.8 → 0.37** (0.8 × 0.468). The threshold is necessary but only reaches
83% alone because IA-281's denominator inflation is row-dependent, not a uniform scale — 1,411 of
5,074 verdict rows (28%) have zero inner-band enemy, and on rows where beyond-320 enemies exist they
average an 18% denominator addition (an approaching enemy just outside 320px is weighted
`distanceWeight(320)=0.875 × contactWeight`, i.e. up to 87.5% of its in-range value where the old
code gave it 0).

## 3. The Cere mechanism

Cere is 4Pool in 100% of games in both batches (311/311 and 456/456 engagement rows). Baseline
56-1; this run 40-10.

**The flap statistic separates winners from losers.** Median early (frame 2000-9000) FIGHT→RETREAT
transitions per game: **22.5 in the 10 losses vs 7.0 in the 40 wins**; baseline wins sat at ~1 early
engagement-retreat. Losses are not slow strangulations — median duration 198 (dataset metric) vs 178
for wins: the all-in is blunted at the door, Cere stabilises, and the game ends shortly after the
Zealot/Reaver ball becomes unbeatable.

**Loss game KU4HQ075, frame by frame.** Our lings: 16 @3250, 20 @4000, 26 @4750, growing to 83 by
~13750 with 10 drones all game (no transition out of the all-in). Cere: 1 Zealot @3250, 3 @4000,
6 @4750, 35 + 7 Dragoons + 4 Reavers by the end. The decision log shows the squad never committing
to the 1-3-Zealot window it used to crush:

- frame 6266: our 5.23 (= 3 Zerglings) vs enemy 10.11 of which **0.00 within 320px**, 7.02 at
  321-480 and 3.08 at 481-640 → ratio 0.52 → RETREAT. Baseline code: enemy unmeasured → ADVANCE.
- frames 5339-5838 (repeatedly): our 7.85 (~4.5 Zerglings) vs Zealots entirely in the 321-640 bands
  → RETREAT, five times in ~500 frames, each starting a 5s retreat lock.
- Contrast the cleanest win, KU4HQ06Z: **zero** early retreat transitions; the squad walks in on
  ADVANCE/ENGAGE against token inner-band strength (0.07-3.76) and wins.

**Why it flaps rather than just retreating once:** `fightLockHolds` breaks a fight lock when the
measured ratio < retreatThreshold (0.8). Ratio 0.8 on the new scale is old-scale 1.71 — so fights
the old code would have locked in as wins now break the lock, and every RETREAT verdict starts a
5-second retreat lock that suppresses re-engagement. Status changes rose 356 → 614 and RETREAT
closes 43 → 213.

## 4. What else I found (defects — reported, not fixed)

1. **The shipped test pins H1's consequence as desired behaviour.**
   `UnitStrengthTest.threeZerglingsNoLongerEngageALoneZealot` asserts 3 Zerglings RETREAT from a
   lone Zealot at 1.4 — i.e. it encodes the doubled bar. No test anywhere guards the old ZvP verdict
   boundary, which was the entire point of scope item 2. CI was structurally blind to H1.
2. **Ultralisk is not neutral.** Dropping the 2.0 hardcode replaced it with `sqrt(400/40)=3.162`,
   a **+58%** price rise, not the "standing in for the missing durability term" swap the commit
   message implies. Neutrality would need a 0.632 residual multiplier.
3. **Lurker 2.5 and Mutalisk 1.5 now double-count durability** (scope item 4, not done): with EHP
   ×1.768 / ×1.732 their prices rose **+77% / +73%** vs the old scale.
4. **Static-defence overrides escape the EHP term** (`UnitStrength.java:52-56`, written after the
   loop). A Sunken Colony's 6.0 was 2.76 old-scale Zealots; it is now 1.38 new-scale Zealots. Both
   sides' static D got ~45% cheaper relative to units — inconsistent with the term's premise, and it
   under-prices our own sunken support in home-defence ratios.
5. **Two shield valuations multiply in the same product.** `hpWeighting` counts shields at ⅓ weight
   (`(3hp+sh)/(3maxHp+maxSh)`) while `effectiveHitPointFactor` counts them at face value.
6. **Enemy armour is absent from the enemy half** (scope item 3, not done).
   `armorUpgradeCorrection` applies only to our units and only to upgrades, at +6% each. A Zealot's
   1 base armour is a 20% real DPS reduction vs 5-damage Zerglings. Note the direction: adding
   armour now would inflate Protoss ~12% further and worsen H1 — it must be sequenced *after* the
   threshold retune, not before.
7. **The Lanchester justification in the `effectiveHitPointFactor` javadoc does not hold as stated.**
   The square law puts *both* halves under the root (`sqrt(dps·hp)`); `dps·sqrt(hp)` is a hybrid.
   For Zealot-vs-Zergling the two differ only 2.49 vs 2.31, so the form is not the regression's
   cause — but the stated derivation is wrong, and the linear-damage-half argument also makes every
   linear correction (upgrades, worker divisor) overweight relative to a Lanchester-consistent form.
8. **EHP_REFERENCE = 40 is an anchor, not a principle.** It pins Zergling (35hp→0.935), Drone, Probe
   and Marine (40hp→1.000) — i.e. it was chosen to keep light units put, which is exactly why only
   the Protoss side of the ratio moved. A supply-normalised form was explicitly allowed by the
   ticket but nothing was measured against it (nor against sqrt-both-halves), so no data on disk can
   rank the forms.
9. **Why Cere fell while Dave Churchill ZvP did not improve** (the thing H1 alone does not explain):
   the engage bar only bites at the margin. Cere games sat exactly on it (baseline verdicts were
   ENGAGE at median ratio 41 — commit-and-win). DC ZvP sits nowhere near it in either batch:
   baseline `sim_ratio_open` median **0.35** (0-12), this run **0.51** (1-17). When the sim already
   says "outnumbered 2:1", extra caution has nothing to trade: it trims losses per game
   (93 → 79 units lost) but halves kills per game (15.3 → 8.8), which is precisely the exchange-rate
   decline 0.162 → 0.108. The DC ZvP problem is army/economy, not engage decisions.

## 5. Recommendation (priority order)

1. **Retune the Protoss thresholds: engage 1.4 → 0.65, retreat 0.8 → 0.37.** Evidence: H1 arithmetic
   (§2), the fitted 0.66/0.60 across Cere and DC ZvP, 49.7% → 83.4% verdict agreement. Cheapest
   experiment: flip the shipped test's expectation (3 Zerglings must ENGAGE a lone Zealot at 0.65)
   and run a 50-game Cere-only batch; expect ≥90% recovery. If ZvZ is the worry, it is untouched —
   these constants live in the `case Protoss` arm.
2. **Pull back IA-281's beyond-horizon weight**, because the threshold alone leaves 2,463 retreats
   vs the baseline 1,641. Either cap the beyond-320 weight below its current 0.875 ceiling or use
   the middle/outer bands only to withhold ADVANCE, not to trigger RETREAT. Evidence: 954 pure-IA-281
   retreats with an empty inner band; halving the bands reaches 91.5% agreement (§2 table). Cheapest
   experiment: the offline counterfactual above is already done — validate with the same 50-game
   Cere batch after step 1.
3. **Keep the effective-HP term itself.** The mirror cancels by construction and measured flat; the
   per-unit re-pricing direction is right. Do not revert the table to fix a threshold bug.
4. **Do not add armour yet** (defect 6): it pushes Protoss prices further up, the wrong way until
   the threshold lands. Sequence it after, with its own threshold re-fit.
5. **Audit the Lurker 2.5 / Mutalisk 1.5 multipliers** (defect 3). Cheapest experiment: a ZvT/ZvP
   mid-game batch comparing exchange rates at 2.5/1.5 vs reduced values; the ZvT exchange move
   (0.255 → 0.485, n.s.) is the only signal on disk touching it.
6. **Restore Ultralisk neutrality** (multiplier 0.632) only if Ultralisks actually appear in the
   bot's games; otherwise it is latent.
7. Leave the static-defence overrides for a follow-up: scaling them into the EHP term (Sunken 6 →
   ~16) would change home-defence behaviour broadly and should not ride along with this fix.

## 6. What I could not determine

- **Game-level attribution.** The verdict level is separable (the band telemetry exists only in the
  new batch, but the baseline ratio is reconstructible per row); the *outcome* level is not — no
  batch exists with either change alone. My 62/27/11 early-window split maps verdicts to behaviour,
  not verdicts to wins; the mapping from "62% of retreat verdicts" to "the 18-point drop" is an
  inference, albeit one backed by the 22.5-vs-7.0 flap split.
- **Whether any threshold setting can satisfy IA-311's ZvP exchange-rate criterion.** DC ZvP's
  baseline ratio median of 0.35 says the ZvP deficit vs strong Protoss bots is structural; 0.65 may
  recover Cere without moving ZvP exchange at all.
- **Whether sqrt(hp) beats sqrt(dps·hp) or a supply-normalised form.** The ticket demanded the
  choice be made against measured engagement results; no such measurement exists on disk, and the
  two-batch confound prevents making one now.
- **The remaining per-opponent moves** (MadMixR −11, liongis −11, Ecgberht −6.3) — all inside their
  Wilson intervals at n=50; with a ~4-point noise floor and known two-batch drift on identical
  code, I cannot say more than "not established".
- **The ticket's old/new strength columns were not independently recomputed from JBWAPI** (the jar
  is not readable from WSL here). I verified the EHP multipliers arithmetically, the internal
  consistency of the brief's table (old × multiplier = new in every row), Cere's Zealot-dominant
  composition from `unit_events.csv`, and the 3-ling/1-zealot boundary against the shipped test's
  own assertions.
- One baseline Dave Churchill game's race could not be derived (unreadable `unit_events.csv`); the
  baseline split I recomputed (12 Protoss / 13 Terran / 25 Zerg) matches the report's 0-12 / 0-13 /
  13-12 accounting.
