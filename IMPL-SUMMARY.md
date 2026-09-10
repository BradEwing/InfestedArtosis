# IA-332 follow-up — the second cause of gas starvation

## What was wrong

`WorkerManager.rebalanceCheck()` runs every frame and decides whether to pull drones off gas:

```java
if (resourceCount.isFloatingGas()) {   // availableGas - availableMinerals > 150
    cutGasHarvesting();                // moves EVERY gas drone back to minerals
} else if (resourceCount.isFloatingMinerals()) {
    saturateGeysers();
}
```

`availableMinerals()` is `self.minerals() - reservedMinerals`, and it goes **negative** whenever the
production queue has reserved more minerals than the bank holds — the normal state of a Zerg bank.
Subtracting a negative number turned a *mineral deficit* into a *gas surplus*: holding 0 minerals
with 75 reserved and 116 gas evaluated as `116 - (-75) = 191 > 150`, so the bot cut every drone off
the geyser while holding 116 gas.

The condition then latched. Un-cutting required `isFloatingMinerals()`, which needs 100+ unreserved
minerals above gas — a state the same over-reservation prevents. Gas workers only returned when a
brand-new drone completed and `assignWorker` happened to route it to the geyser, and the next frame
cut it again.

Nothing in IA-332's Problem section describes this. It is a separate defect in the worker layer,
downstream of both the bank deadlock IA-332 fixed and the geyser-reservation leak IA-328 fixed,
which is why AC5 and AC6 improved while AC7 did not move.

### Worked example — `L4KVD0DW` (MicRobot, `3HatchMuta`, batch `20260909-184513`)

```
f3713   Zerg_Extractor -> COMPLETE
f5397   minerals 0,  gas 100, available_minerals -75, available_gas 100  -> cut fires
f5555   minerals 16, gas 116, available_minerals -59, available_gas 116  -> still cut
f5555 .. f13797 (game end)   gas_gathered frozen at 216
```

8,242 frames with an extractor standing and zero gas mined. Total gas gathered: 216.

## Evidence — batch `20260909-184513`

512 games carry plan telemetry; 402 completed an Extractor; **214 of those (53%) ended with
`gas_gathered <= 400`**.

Of the 202 low-gas games with any post-extractor cut frame, **154 (76%) had their *first* cut fire
spuriously** — `available_minerals < 0` and `available_gas <= 150`, i.e. driven by the mineral
deficit, not by gas. Median gas mined after that first spurious cut: 192.

Both predicates were replayed offline over all 1,005,070 post-extractor telemetry rows in those 402
games, split by whether the game ended holding unspent gas:

| Group | Games | Cut frames before | after | Saturation frames before | after |
|---|---|---|---|---|---|
| Ended holding **<150** unspent gas (the bot wanted the gas) | 91 | 16,602 | **6,058 (-64%)** | 3,296 | **16,263 (+394%)** |
| Ended holding **>=150** unspent gas (gas genuinely floating) | 123 | 209,923 | 206,294 (-2%) | 1,711 | 5,424 |

The fix removes two thirds of the cutting exactly where the bot needed the gas, and leaves the case
where gas really is floating almost untouched.

## What changed

`src/main/java/unit/WorkerManager.java`

- `shouldCutGasHarvesting(availableMinerals, availableGas)` — package-private static. Floors the
  mineral side at zero: `availableGas - max(0, availableMinerals) > GAS_SURPLUS`. A mineral deficit
  can no longer masquerade as a gas surplus. A genuine surplus still cuts, at the same 150 bar.
- `shouldSaturateGeysers(availableMinerals, availableGas)` — package-private static. Keeps the old
  floating-minerals rule and adds `availableGas <= 0`. This is what un-cuts: when no unreserved gas
  is left, an under-manned geyser gets drones back regardless of the mineral bank.
- `spareGeyserWorkers(mineralGatherers, openSlots)` — package-private static. Saturation is now
  reachable at drone counts it never saw before, so it applies the same `MIN_MINERAL_GATHERERS`
  floor `onExtractorComplete` already used, rather than being free to move the last mineral drone.
- `rebalanceCheck()` reads the two resource figures once and calls the predicates.
- The literal `4` in `onExtractorComplete` became `MIN_MINERAL_GATHERERS`; behaviour unchanged.

`src/main/java/info/ResourceCount.java`

- Deleted `isFloatingGas()`. `WorkerManager` was its only caller and it is the broken expression.

`src/test/java/unit/WorkerManagerTest.java` — new, 11 tests, junit-jupiter only, no mocks: the
`L4KVD0DW` numbers as a regression case, both thresholds at their boundaries, the mineral-deficit
case, the un-cut case, the gatherer floor, and a sweep asserting the cut and the saturation are
never both true.

## Acceptance criteria

ACs 1-6 and 8 were met by IA-332 as shipped and are untouched by this branch. This work targets AC7.

| AC | Status | How verified |
|---|---|---|
| 1 Metabolic Boost gated on living zerglings | Met by `8e4159e`, not re-touched | — |
| 2 Gas plan cannot bank-block an Extractor at zero gas income | Met by `8e4159e` (2.2% vs 7.5%) | ticket comment |
| 3 `NinePoolSpeed` gas branch reachable | Met by `8e4159e` | ticket comment |
| 4 Unit tests at the extracted-predicate seam | **Met** | 11 new tests, `mvn -o package` exit 0 |
| 5 No Extractor blocked >1000f on `BUILD_AHEAD_SLOT_TAKEN` | Improved by `8e4159e`, unaffected here | this branch touches no plan or queue code |
| 6 No `Metabolic_Boost` blocked >2000f on `RESOURCES` | Improved by `8e4159e`, unaffected here | as above |
| 7 Extractor completes -> total gas > 400 | **Not verifiable on this branch; expected to move for the 91-game subset only** | offline replay above; needs a fresh batch |
| 8 `mvn -o package` and `mvn -o checkstyle:check` exit 0 | **Met** | 664 tests pass, both exit 0, `git diff --check` clean |

### AC7 will not fully pass, and the criterion is the reason

The 214 failing games split in two, and only one half is a defect:

- **91 games ended holding under 150 unspent gas.** They wanted gas and did not get it. This is the
  bug fixed here — 67 of those 91 had a spurious first cut.
- **123 games ended holding 150 or more unspent gas.** Mean gathered 259, mean spent 97. Gas floated
  because nothing in the queue consumed it, so cutting the geyser was the *correct* call. Mining
  past 400 there is waste, and the fix deliberately leaves that path alone (-2%).

AC7 as written ("in games where an Extractor completes, total gas gathered must exceed 400") does
not distinguish the two and cannot be satisfied by worker assignment alone. **The demand side
warrants its own ticket**: an extractor is planned off `needExtractor()` / floating minerals with no
check that any gas-consuming plan is coming, so the bot buys a 50-mineral building and a 3-drone
detour for gas it will never spend. Evidence is the 123-game group above. That is not implemented
here.

## Telemetry

**No column added.** The header stays at 38 and `PlanEventLoggerTest.PLAN_COLUMNS` is untouched.

`gas_gathered` (col 38) already grounds AC7, and `available_minerals` / `available_gas` (cols 19-20)
carry exactly the two inputs of the predicate, which is how both the old and new rules were replayed
offline over a million rows above.

What is *not* emitted is the mineral/gas worker split — `gatherers` is a single total, so the cut
itself is only inferable, not observed. A `gas_workers` column would make it direct. Given the
brief's warning that three tickets adding columns at once caused the last beta's worst conflict, and
that the existing columns proved sufficient, I did not add one. If a future batch needs it, it
belongs in `appendGameTotals`.

## Merge risk for the coordinator

**`ResourceCount.java` overlaps IA-338.** I deleted `isFloatingGas()`, the four lines immediately
below `isFloatingMinerals()` — which is the method IA-338 renames. Expect a conflict at
`ResourceCount.java:252-256`; the resolution is to keep both changes (their rename, my deletion).

**`WorkerManager.rebalanceCheck()` no longer calls either `ResourceCount` predicate.** If IA-338
updates that call site for its rename, it will conflict at `WorkerManager.java:478-486`; my version
is the newer one and needs no rename applied.

Neither `GameState.java`, `BuildOrder.java`, `Reactions.java`, `ProductionManager.java` nor
`PlanEventLogger.java` is touched.

## Adjacent defects found and deliberately left alone

1. **Gas demand is unchecked** — `GameState.canPlanExtractor()` (`GameState.java:838`) plans an
   extractor off floating minerals with no requirement that anything will spend the gas. 123 games
   in this batch. Own ticket, described above.
2. **`earlyRushDenyGas` is one-way** — it suppresses `canPlanExtractor()` and nothing re-plans the
   extractor once the rush is survived. Not examined further.
3. **`cutGasHarvesting()` is all-or-nothing** — it moves every gas drone off rather than trimming to
   the surplus, so a 151-gas surplus costs the whole geyser. Threshold and granularity tuning is
   outside this fix.
4. **`ResourceCount.isFloatingMinerals()` vs `HatcheryCapacity.isFloatingMinerals()`** — the name
   collision is IA-338's scope; untouched here.
