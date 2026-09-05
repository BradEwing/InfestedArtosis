# Verdict

The effective-HP idea is **mistuned, not fundamentally misguided**. Damage-only strength plainly omits a combat-relevant difference between a 35-HP Zergling and a 160-point Zealot, and the clean ZvZ canary is evidence that the implementation is doing relative repricing rather than introducing a general arithmetic failure. The regression is instead the predictable result of changing the cross-race ratio without retuning the decision thresholds, then simultaneously putting more enemies into the denominator. My confidence is 80%. I would keep the square-root durability term provisionally, but give early Zergling-only ZvP squads an engage threshold of **0.62** and a retreat-lock break threshold of **0.35**, instead of 1.4 and 0.8. I would change my mind if an isolated factorial batch showed that IA-311 with those thresholds still loses Cere games or fails to improve exchange rate while damage-only strength under the same IA-281 setting does not.

# H1: partly confirmed

H1 is confirmed as a scale defect and partly confirmed as the cause of the Cere regression. It cannot be confirmed as the sole cause because the batch changed two numerator/denominator mechanisms at once.

For Zerglings against a Zealot, IA-311 multiplies the ratio by 0.935 / 2.000 = 0.468. Thus the unchanged 1.4 engage threshold is equivalent to 2.99 on the old scale. Against a Dragoon the factor is 0.935 / 2.121 = 0.441, making 1.4 equivalent to 3.17. The argument that thresholds remain valid because Zergling strength changed only 1.865 to 1.744 examines one side of a ratio and is unsound. The mirror factor is exactly one, consistent with Dave Churchill ZvZ remaining 53% versus 52% and with its exchange rate not regressing.

Mapping the old thresholds onto the new scale gives:

| Enemy | Engage | Retreat-lock break |
|---|---:|---:|
| Zealot | 1.4 x 0.468 = **0.655** | 0.8 x 0.468 = **0.374** |
| Dragoon | 1.4 x 0.441 = **0.617** | 0.8 x 0.441 = **0.353** |

The raw decision distribution independently lands in the same range. In baseline Cere games, 54.9% of measured decision rows cleared 1.4. In the new run, a threshold of 0.62 makes 54.4% clear, while the exact quantile match is 0.6117. The old fraction below the 0.8 retreat threshold was 21.3%; the exact new-run quantile match is 0.3028, lower than the per-unit 0.35-0.37 mapping because IA-281 also depresses ratios. I therefore recommend 0.62/0.35 as conservative, round starting constants for the affected early Zergling composition, not as proven universal ZvP constants.

IA-281 is material and separately visible. Across 5,064 measured raw Cere decision rows, summed enemy strength was 72.8% inner (0-320), 17.8% middle (321-480), and 9.4% outer (481-640). In other words, IA-281 added 27.2% of the observed denominator; for Dave it added 31.5%. Removing the added bands in a static re-score would turn at least 430 Cere rows from below 1.4 to at or above it. That counterfactual is incomplete because rows with only outer-band enemies would become ADVANCE, and changing one decision changes all later positions and samples.

The ninefold engagement-opening median fall, 19.36 to 2.13, therefore does not measure a ninefold strength change. A Zealot supplies about a 2.14-fold ratio reduction and the summed band telemetry supplies roughly another 1.37-fold denominator increase relative to inner strength. Their coarse product is about 2.9, not nine. The rest is selection and trajectory change: ADVANCE openings collapse from 72 to 11, engagements increase from 311 to 456, losing games last through many repeated contacts, and prior decisions change later squad composition and position. These are different populations, not paired observations. The disk data cannot allocate the remaining difference uniquely.

# The Cere mechanism

Cere is the clean outcome signal because both batches choose 4Pool every game. The all-in succeeds when Zerglings stay attached to targets; the new scale and horizon split squads into repeated retreat/re-engage cycles, and the locks make each low-ratio sample persist beyond the frame that produced it.

`KU4HQ075` is a concrete loss. At frame 3993, a three-supply squad has ratio 1.447 against one inner-band Zealot, which is an ENGAGE under 1.4, but a retreat lock suppresses it until frame 4113. On the same frame, a half-supply fragment sees 2.563 strength entirely in the outer band, gets ratio 0.321, and enters RETREAT. At frame 4113 the main squad finally re-enters FIGHT at ratio 2.406 against an entirely outer-band threat. Three frames later another half-supply fragment sees 4.490 inner strength, falls to 0.100, and starts a new five-second retreat lock. At frame 4241 a FIGHT lock temporarily suppresses a RETREAT verdict at ratio 1.237; when that lock expires at frame 4257, the squad retreats at ratio 1.138 against an enemy entirely in the outer band. Later examples repeat: frame 5341 retreats at 0.796 with all enemy strength in the two added bands; frame 5833 retreats at 0.691 with middle-band strength; frame 5838 retreats at 0.666 with outer-band strength. The first Zergling losses begin at frames 4465, 4681, and 4862; only two Probes have died by then. The game eventually loses the Hatchery at frame 15824.

`KU4HQ07O`, another loss, shows the same mechanism without relying on one pathological squad. At frame 5338 a squad retreats at ratio 1.029 solely because 2.544 enemy strength is in the outer band. At 5838 it retreats at 0.569 against one inner Zealot. At 6402 the denominator spans all three bands and the ratio is 0.515. Through frame 7054, eleven Zerglings have died for only two Probes. This is both IA-311 (the close Zealot price) and IA-281 (pre-contact retreats).

The contrast is `KU4HQ06Z`, a win. At frame 4200 its five-supply squad engages at ratio 2.954. At frame 4804 the ratio falls to 1.354, but the active FIGHT lock holds the attack. The bot kills Zealots at frames 4254, 4439, and 4884, then a stream of Probes and Nexuses; it records no FIGHT-to-RETREAT transition in the inspected attack window. This is consistent with commitment being the decisive behavioral difference, though logs alone cannot prove the counterfactual result of a different verdict.

Across the entire new Cere run, losses average 63.5 FIGHT-to-RETREAT transitions per game versus 7.3 in wins. That is strong association, not independent causal identification: losing games also last longer and generate more opportunities to flap. The five-second retreat lock and three-second fight lock amplify changed ratios; they do not originate the repricing.

# What else I found

The Lanchester justification in `effectiveHitPointFactor` does not describe the implemented formula. The javadoc invokes `sqrt(dps * effectiveHitPoints)`, but the code computes `dps * sqrt(effectiveHitPoints / 40)`. Leaving damage linear because later corrections are linear is an engineering compatibility argument, not a derivation from the square law. This simulator sums scalar values, applies distance and health heuristics, and selects a threshold; it does not model the homogeneous, continuously firing forces assumed by that law. The durability term may still be a useful fitted feature, but it should be documented and tested as one.

`EHP_REFERENCE = 40` is not a cross-race calibration knob. Its denominator is common to every normally computed unit and cancels out of dynamic-unit ratios. It only changes their scale relative to hardcoded static-defense overrides. At 40, the Zergling factor is 0.935 while a Sunken remains exactly 6, so the Sunken becomes about 7% stronger relative to a Zergling. The existing test explicitly permits that. Static defense is already an override and should remain independently tuned; automatically giving it a building-sized EHP multiplier would be unjustified, but claiming that 40 preserves all thresholds is incorrect.

Supply-normalizing the durability factor is not supported by these data. For the ticket's values, `(HP + shields) / supply` makes Zergling and Zealot durability per supply nearly equal and would mostly erase the feature the change is trying to add. Supply is a balance/economy constraint, not observed combat durability. It may be a useful regularizer for a learned model, but it is not a principled replacement here.

Counting shields at face value in maximum durability is reasonable for the initial fight because Zerglings must remove them. Regeneration could make shields more valuable in the exact retreat/re-engage pattern seen here, not less. However, the current-health factor weights HP three times as heavily as shields, while maximum durability weights them equally. That internal inconsistency is not tested. The data do not identify better shield or health weights.

Base armor is absent from the table. The brief's Zealot example means one armor reduces a five-damage Zergling hit by 20%, so the current term is not actually effective HP and still understates Zealot durability in that pairing. Armor cannot be represented correctly by a single unit-only constant because its effect depends on the attacker and upgrades. Adding it now would push Cere ratios still lower and confound the missing threshold retune; it should wait for a matchup-aware model.

The Ultralisk 2.0 override was removed, but Lurker 2.5 and Mutalisk 1.5 remain and the new tests merely pin that choice. This batch contains no controlled Lurker or Mutalisk comparison capable of showing whether those values now double-count durability. Their presumed splash/bounce or tactical compensation cannot be separated from durability from these logs.

There is also a pre-existing domain asymmetry: friendly units use `UnitStrength.totalStrength`, summing both ground-target and air-target weapon entries, while enemy strength uses only the domain relevant to the squad. Dual-capable friendly units can therefore contribute both target domains to a verdict whose enemy denominator represents one domain. It does not explain Zergling-only Cere, but it can distort Dave's varied compositions and should be reported as a separate simulator defect.

The tests would not catch H1. `UnitStrengthTest` verifies the intended repricing, mirror cancellation, and that three Zerglings retreat while four engage a Zealot at the unchanged 1.4; it encodes the changed verdict rather than validating the threshold against outcomes. `HorizonCombatSimulatorTest` tests band helpers and scalar selection. `SquadVerdictTest` tests locks given supplied ratios. No test checks cross-race ratio-distribution preservation, composition-dependent threshold mapping, the interaction of added bands with EHP, or engagement exchange rate.

Dave does not contradict the Cere mechanism. Dave is a random-race opponent with varied openers and build orders, whereas Cere is a pure ZvP 4Pool test. Dave's ZvP result moved from 0-12 to 1-17: there was almost no baseline win-rate headroom to reveal a further regression, and the sample sizes are too small to distinguish those rates. Its overall results also mix thresholds of 1.0, 1.3, and 1.4. More caution can be costly for an already-successful all-in and neutral for a matchup that was already losing; the two opponents need not move together.

# Recommendation

1. **Retune the affected all-in before judging EHP:** use Protoss engage **0.62** and retreat-lock break **0.35** for early Zergling-only/4Pool squads. These values agree both with the analytical Zealot/Dragoon mappings (0.617-0.655 and 0.353-0.374) and with the measured engage-distribution match (0.6117). Do not apply 0.62 blindly to later Hydra-heavy ZvP: a Hydralisk's 1.414 multiplier versus a Zealot's 2.0 implies a different mapped engage threshold near 0.99. The cheapest causal test is 50 Cere games with only this threshold override, compared against the current integration branch with frozen learning inputs.

2. **Run the missing factorial:** release baseline, IA-311 only, IA-281 only, and both, using the same fixed maps/seeds and learning files. At minimum run Cere and Dave's actual-Protoss subset. This is the cheapest experiment that can attribute the outcome regression. The current two-change batch cannot do so, even though raw bands can quantify IA-281's denominator contribution.

3. **Keep `sqrt((HP + shields) / 40)` provisionally, but remove the false derivation and treat the exponent as a fitted constant.** Compare exponents 0, 0.25, and 0.5 after threshold calibration, scoring predicted verdicts against observed exchange and outcomes rather than only win rate. Keep shields at 1.0 and add no armor in this experiment so only one feature moves. The 40 reference may remain for compatibility with overrides; changing it cannot repair dynamic-unit ratios.

4. **Make thresholds composition-aware if EHP survives.** A single race threshold cannot preserve sensible behavior for Zergling-vs-Zealot and Hydra-vs-Zealot simultaneously. Start with the explicit Zergling-only override above rather than a global 0.62. The cheapest validation is an offline re-score of recorded decision rows followed by a small replay batch, because offline scoring cannot capture changed trajectories.

5. **Audit separate heuristics separately:** hold Lurker 2.5, Mutalisk 1.5, static-defense overrides, shield weighting, and armor unchanged during the threshold experiment. Then test each on engagements where that feature is present. The present batch has no evidence for changing their constants.

# What I could not determine

- I could not assign the Cere win-rate loss between IA-311 and IA-281. Both lower the same ratio, and every behavioral decision changes subsequent positions, squads, and engagements.
- I could not decompose the full 19.36-to-2.13 median shift into stable percentages. Per-band telemetry quantifies IA-281 in the new run only; the baseline has no band fields, and the engagement populations differ.
- I could not establish that exponent 0.5 is optimal, only that durability is a plausible feature and the old thresholds are incompatible with its current scale.
- I could not infer universal ZvP thresholds from a Zergling-only opponent and 18 noisy Dave ZvP games. The recommended 0.62/0.35 constants are intentionally scoped to the demonstrated early Zergling composition.
- I could not measure armor, shield-regeneration, Lurker, Mutalisk, or static-defense effects independently from these batches.
- I could not prove from logs that a specific retreated Cere game would have won after engaging; that requires a rerun. The frame traces demonstrate the mechanism and association, not the alternate outcome.

INVESTIGATION COMPLETE
