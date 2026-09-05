# Aggregate — three-agent investigation of ZvP and the Tomas Cere regression

Agents: **Claude** (Opus 5), **Codex** (gpt-5.6-sol), **Pi** (zai/glm-5.3). Dispatched independently
into the same worktree with the same brief, no coordination. Individual reports:
`FINDINGS-claude.md`, `FINDINGS-codex.md`, `FINDINGS-pi.md`.

Claims marked **[verified]** were independently recomputed or read out of the code by the
orchestrator, not taken on the agent's word.

---

## The answer: mistuned, not misguided. 3 of 3, independently.

All three keep the effective-HP term and all three locate the fault in the same place: **IA-311's
scope item 2 — the threshold retune — was skipped, and the argument used to justify skipping it is
unsound.**

That argument checked that *our* units kept their scale (Zergling 1.865 → 1.744) and concluded the
thresholds still held. But the threshold is a **ratio**, and only the enemy side inflated:

| unit | multiplier |
|---|---|
| Zergling | 0.935 |
| Marine | 1.000 |
| Hydralisk | 1.414 |
| **Zealot** | **2.000** |
| **Dragoon** | **2.121** |

ZvP ratio scale ×0.468 → **the unchanged 1.4 engage bar became an effective 2.99** on the old scale.
The mirror multiplier is exactly **1.000**, which is why ZvZ stayed flat (10-9 vs 13-12) *and* why
checking our own units made the reasoning look sound. All three agents derived this independently and
identically. **[verified]** — arithmetic reproduced.

**There is no implementation fault in the term itself.** The mirror cancels by construction and
measured flat; the repricing direction is right. Claude's frame-level duel model puts the true
break-even at **3 Zerglings per Zealot**; classical Lanchester puts it at 2.31; the old table said
1.16 and the new table says 2.49. The new table is much closer to reality than the old one.

---

## Where they disagree — and it matters

### 1. The constant. 0.62 / 0.65 / 0.9.

| agent | engage | retreat | basis |
|---|---|---|---|
| Codex | 0.62 | 0.35 | analytic mapping + empirical quantile match (0.6117) |
| Pi | 0.65 | 0.37 | analytic mapping + 83.4% verdict agreement |
| **Claude** | **0.9** | **0.5** | frame-level duel model + measured exchange-by-ratio curve |

Codex and Pi both solve for *restoring the old ZvP behaviour* (`1.4 × 0.468 = 0.655`). Claude argues
that restores a bar that was itself wrong — the old code demanded 2.17 Zerglings per Zealot against a
true break-even of 3, i.e. it was ~30% too aggressive, and 0.9 demands 2.99.

**The batch's own data backs Claude. [verified]** — exchange rate by `sim_ratio_open` bucket, Cere:

| bucket | n | exchange |
|---|---|---|
| 0–0.5 | 105 | 0.25 |
| 0.5–0.8 | 30 | 0.80 |
| **0.8–1.0** | **13** | **1.25** |
| 1.0–1.2 | 11 | 0.96 |
| 1.2–1.4 | 17 | 0.98 |
| 1.4–1.8 | 32 | 1.34 |

Break-even is crossed at **ratio ≈0.8**, not 1.4. The bot was declining fights it wins. Caveat: the
middle buckets are thin (n=11–17) and this is observational — Claude flagged it itself and did *not*
use it to derive 0.9, keeping the two routes independent.

**Resolution: test both.** 0.9/0.5 as the primary arm, 0.655/0.37 as the control. They are cheap and
they differ by a real behavioural margin.

### 2. Which change did more damage. Pi says IA-311; Claude says IA-281.

- **Pi**: in the early window (<frame 6000) that decides 4Pool games — IA-311 **61%**, IA-281 **28%**,
  would-have-retreated-anyway 11%. **[verified]** — I recomputed across all 50 Cere games and got
  27.6 / 11.5 / **61.0**, matching to within a few rows.
- **Claude**: over all rows, IA-281 accounts for ~1,374 extra RETREATs (33%) against IA-311's
  510–1,180 (12–28%), so **IA-281 is the larger single contributor** — and if only one single-change
  batch is affordable, run **IA-281 alone first**.

**This is a methodology difference, not a contradiction.** Pi's counterfactual removes both effects at
once and assigns the jointly-flipped rows to IA-311; Claude removes IA-281 first, then IA-311 on top,
which is a proper sequential decomposition where order matters. Claude is the more rigorous
construction and is honest that its IA-311 term is a *bracket* (×1.07 all-Probe to ×2.14 all-Zealot)
because per-frame enemy composition is not logged.

**What is not in dispute: both changes are material, and neither report can attribute the 18-point
outcome drop.** Only a single-change batch can.

### 3. Scope of the threshold. Codex alone objects to a global Protoss constant — a Hydra-heavy ZvP
maps to ~0.99, not 0.65, because the Hydralisk multiplier is 1.414 rather than 2.000. Worth heeding:
the fix should be scoped to the early Zergling composition or made composition-aware, not applied
blindly across all of ZvP.

---

## The finding that reframes the ticket

**IA-311 was aimed at a problem the combat simulator does not own.** Claude and Pi reached this
independently.

Non-Cere ZvP: **3-36 (7.7%) → 3-48 (5.9%), Fisher p = 1.00.** Nothing moved, in either direction.

Those games are not lost on engage decisions. Median 20,555 frames, 109 of our units lost against 14
of theirs, peak supply 57 — a 1:8 army-value deficit no engage threshold can recover. Pi measured the
same thing from the other end: Dave Churchill ZvP sits at a median `sim_ratio` of 0.35 baseline / 0.51
now, nowhere near the bar, so extra caution has nothing to trade — it trimmed losses 93→79 units per
game while halving kills 15.3→8.8, **which is exactly the 0.162→0.108 exchange-rate decline.**

The ticket's ZvP exchange-rate acceptance criterion was never achievable by a strength-table change.
Cere moved because Cere is the one ZvP matchup sitting *on* the engage bar; everything else in ZvP is
an economy and composition problem.

Tomas Cere itself: **Fisher p = 0.0026** — genuinely above the noise floor, unlike every other
per-opponent move in the batch.

---

## Defects found, none previously ticketed. All [verified] in code.

1. **The containment flap** (Claude only, and the best find in the batch). `SquadManager.java:895`
   calls `tryEnterContainment` on a RETREAT verdict; `evaluateContainingSquad` (`:1051-1058`) sees any
   enemy within `ENEMY_DETECTION_RADIUS` and immediately forces FIGHT with a fresh lock. Against Cere:
   containment entries **1.5 → 54 per game**, 96.1% lasting ≤2 frames, 2,701 CONTAIN→FIGHT transitions,
   median FIGHT dwell **1 frame**. Pre-existing (VOID showed it on the baseline) but amplified ~36×.
2. **`OUTER_HORIZON_RADIUS` (640) exceeds `ENEMY_DETECTION_RADIUS` (512).** The simulator now reasons
   about a band the squad manager cannot see — the direct cause of (1). `NEARBY_THREAT_RADIUS` also
   moved 512 → 832 with no separate justification.
3. **`EHP_REFERENCE = 40` is a no-op for every mobile-unit verdict.** It is a common factor that
   cancels in any ratio of loop-computed strengths. Its *only* effect is on the five hardcoded static
   overrides, which are written after the loop and never see it. All three agents found this; it
   directly refutes the constant's own javadoc.
4. **Static defence is now half-priced.** Sunken/Cannon 6 against a Zealot: 2.77 → **1.38**.
   Loop-consistent values would be Sunken 16.4, Cannon 13.4, Bunker 35.5. Risk: walking into cannon
   walls. No opponent on this bench builds Cannons, so the batch cannot show the harm.
5. **The tests would not catch H1 — one blesses it.** `threeZerglingsNoLongerEngageALoneZealot`
   (`UnitStrengthTest.java:97`) asserts 3 Zerglings retreat from a lone Zealot, but 3 Zerglings *beat*
   a Zealot (killing it for the loss of 2, a 2:1 mineral trade). The mirror and Marine tests sweep
   multipliers that are 1.000 by construction and cannot fail. CI was structurally blind.
6. **Ultralisk was not a neutral removal.** Dropping the 2.0 hardcode left `sqrt(400/40)` = 3.162, a
   **+58% price rise**, not the like-for-like swap the commit implies.
7. **Two shield valuations multiply in one product.** `hpWeighting` counts shields at ⅓ weight
   (`(3hp+sh)/(3maxHp+maxSh)`); `effectiveHitPointFactor` counts them at face value. Untested.
8. **Friendly/enemy domain asymmetry.** `computeFriendlyStrength` uses `totalStrength` (all four
   domains summed) while the enemy path splits ground and anti-air. Traced to IA-174 (`e1d6dff`) —
   pre-existing, neutral for Zergling-only Cere, distorting for mixed compositions.
9. **The Lanchester javadoc justifies a formula that was not written.** The law implies
   `sqrt(dps · EHP)`; the code ships `dps · sqrt(EHP)`. All three agents flagged it. The form is
   defensible empirically — keep the code, delete the derivation.
10. **Reaver 0 / Scarab 335.** Pre-existing and already in project memory. Claude showed it *seals*
    the Cere losses (ratio collapses to ~0.03, squad can never leave RETREAT) but does not cause them —
    first such row appears frame 11,235+, long after the 4,000–8,000 divergence.

Also worth recording: **the Cere wins are indistinguishable from the baseline's wins** (Probes killed
by frame 8000: 15.6 vs 16.3; lings built 21.8 vs 21.6). The regression is bimodal — 40 games unchanged
plus 10 games with a new failure mode (3.3 Probes killed, 18.3 lings lost), not a uniform degradation.

---

## Recommended sequence

1. **Retune the ZvP thresholds** — engage 1.4 → **0.9**, retreat 0.8 → **0.5**. Two arms: 0.9/0.5 and
   0.655/0.37. Leave the Zerg arm (1.3) alone — the mirror measured ×1.01 and ZvZ is flat.
   *Experiment: 50 games vs Tomas Cere only, ~25 min at `--jobs 7`. Bandit-locked to 4Pool, so no
   strategy confound; p=0.0026 makes it the highest-signal opponent on the bench.*
2. **Fix the containment flap** — one guard at `SquadManager.java:895`. Separate ticket; pre-existing.
3. **Cap `OUTER_HORIZON_RADIUS` at 512**, or gate the outer bands on the enemy actually approaching.
   Currently a stationary Zealot 600px away is priced as if charging.
4. **Bring the static-defence overrides onto the new scale.** Separate arm — do not confound with (1).
5. **Fix the test that asserts the regression.**
6. **Do not add armour yet.** It raises Protoss prices ~12% further and would deepen the error until
   the thresholds land.
7. **Reframe ZvP.** File the non-Cere ZvP economy/composition problem separately. No strength-table or
   threshold change will move 3-48.
8. **Run the missing factorial** if budget allows — baseline / IA-311 only / IA-281 only / both. It is
   the only thing that can attribute the outcome. Claude would run **IA-281 alone first**.

## What none of the three could determine

- **Outcome attribution between the two tickets.** Verdict *inputs* separate cleanly; outcomes do not.
- **Whether `sqrt` is the right exponent.** The ticket demanded the form be chosen against measured
  engagement results; no such measurement exists on disk and the two-change confound prevents making
  one now. Supply-normalisation was checked and rejected by two agents — EHP per supply is 80 (Zealot)
  vs 70 (Zergling), which would delete the term.
- **Whether Lurker 2.5 / Mutalisk 1.5 double-count.** No ZvP Lurker or Mutalisk volume on this bench.
- **Whether the containment flap costs games.** The mechanism and its 36× amplification are measured;
  the damage is inference (no per-unit order telemetry).
- Cross-batch distribution comparisons are biased by an endogenous row population — the new code emits
  3.4× more decision rows per Cere game, and more retreats produce more rows.
