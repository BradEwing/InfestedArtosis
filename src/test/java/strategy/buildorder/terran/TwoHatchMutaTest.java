package strategy.buildorder.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
import macro.DroneRound;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;
import telemetry.PlanEvents;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchMutaTest {

    @Test
    void theFirstWaveHoldsBackCalmRoundsOnlyWhileASpireStandsAndFewerThanSevenAreProduced() {
        assertTrue(TwoHatchMuta.holdsFirstWave(true, 0));
        assertTrue(TwoHatchMuta.holdsFirstWave(true, 3));
        assertTrue(TwoHatchMuta.holdsFirstWave(true, TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE - 1));
        assertFalse(TwoHatchMuta.holdsFirstWave(true, TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE));
        assertFalse(TwoHatchMuta.holdsFirstWave(false, 0));
    }

    @Test
    void aCalmRoundOpensOnlyOnceTheFirstWaveIsProduced() {
        int workers = DroneRound.CALM_ECONOMY_WORKER_DEFICIT;
        DroneRound spireWithThree = new DroneRound();
        DroneRound spireWithSeven = new DroneRound();
        DroneRound noSpire = new DroneRound();
        int frame = DroneRound.CALM_ECONOMY_FRAMES;

        spireWithThree.update(frame, 0, 10, 30, true, false, calmHeld(workers, TwoHatchMuta.holdsFirstWave(true, 3)));
        spireWithSeven.update(frame, 0, 10, 30, true, false, calmHeld(workers, TwoHatchMuta.holdsFirstWave(true, 7)));
        noSpire.update(frame, 0, 10, 30, true, false, calmHeld(workers, TwoHatchMuta.holdsFirstWave(false, 0)));

        assertFalse(spireWithThree.isActive());
        assertEquals(DroneRound.OpenReason.CALM_ECONOMY, spireWithSeven.getReason());
        assertEquals(DroneRound.OpenReason.CALM_ECONOMY, noSpire.getReason());
    }

    private static DroneRound.ContainHeld calmHeld(int deficit, boolean held) {
        return DroneRound.ContainHeld.builder()
                .softCap(30)
                .hardCap(40)
                .workers(30 - deficit)
                .calmEconomyHeld(held)
                .build();
    }

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final int NO_LARVA = 0;

    private static final int ONE_HATCHERY = 1;

    private static final int TWO_HATCHERIES = 2;

    private static final int NO_ENEMIES = 0;

    private static final int NO_MACRO_HATCHERY = 0;

    private static final int ONE_MACRO_HATCHERY = 1;

    private static final int LARVA_STARVED_FRAME = 9917;

    private static final TilePosition MAIN_TILE = new TilePosition(117, 119);

    private static final int FIRST_BACKLOG_FRAME = 6338;

    private static final int BACKLOG_PLANS = 19;

    private static final int BACKLOG_STEP = 100;

    private static final int WAVE_TARGET = 11;

    private static final int FLOATING_TARGET = 14;

    private static final int LOST_MUTALISKS = 4;

    private static final int FRAMES_PER_LARVA = 12;

    private static final int WAVE_FRAMES = FRAMES_PER_LARVA * (FLOATING_TARGET + 5);

    private static final int PLANNED_MUTALISKS = 7;

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    @Test
    void derivesTheMutaliskWithASpireAndTheGathererFloor() {
        assertTrue(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskBelowTheGathererFloor() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 0, 9, GATHERER_FLOOR - 1));
    }

    @Test
    void withholdsTheMutaliskWithoutASpire() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(new TechProgression(), 0, 9, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanMutalisk(withSpire(), 9, 9, GATHERER_FLOOR));
    }

    /**
     * Game LBIDH0GO frame 8,233: the seventh Mutalisk plan was queued with none alive, and Flyer
     * Attacks was queued on the same frame.
     */
    @Test
    void withholdsFlyerAttacksWhileTheMutalisksAreOnlyPlanned() {
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 0);

        assertTrue(count.get(UnitType.Zerg_Mutalisk) > 6);
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void withholdsFlyerAttacksWithSixLivingMutalisksAndMorePlanned() {
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 6);

        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void plansFlyerAttacksWithSevenLivingMutalisks() {
        UnitTypeCount count = mutalisks(0, 7);

        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void withholdsFlyerAttacksWithoutASpire() {
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(new TechProgression(), 7));
    }

    /**
     * Game LBIDH0GO frame 12,830: level 1 finished and level 2 was queued the same frame with no
     * Mutalisk alive.
     */
    @Test
    void withholdsTheSecondLevelOnTheFrameTheFirstCompletesWithoutLivingMutalisks() {
        TechProgression techProgression = withSpire();
        techProgression.setPlannedFlyerAttack(true);
        UnitTypeCount count = mutalisks(PLANNED_MUTALISKS, 3);

        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, count.livingCount(UnitType.Zerg_Mutalisk)));

        techProgression.setFlyerAttack(1);
        techProgression.setPlannedFlyerAttack(false);

        assertTrue(techProgression.canPlanFlyerAttack());
        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, count.livingCount(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void plansTheSecondLevelOnceTheFirstCompletesWithSevenLivingMutalisks() {
        TechProgression techProgression = withSpire();
        techProgression.setFlyerAttack(1);

        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, 7));
    }

    @Test
    void withholdsTheSecondLevelWhileTheGatherersAreBelowTheWorkerFloor() {
        TechProgression techProgression = withSpire();
        techProgression.setFlyerAttack(1);

        assertFalse(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, 7,
                TwoHatchMuta.WORKERS_BEFORE_LATER_FLYER_UPGRADES - 1,
                TwoHatchMuta.WORKERS_BEFORE_LATER_FLYER_UPGRADES));
        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(techProgression, 7,
                TwoHatchMuta.WORKERS_BEFORE_LATER_FLYER_UPGRADES,
                TwoHatchMuta.WORKERS_BEFORE_LATER_FLYER_UPGRADES));
    }

    @Test
    void plansTheFirstLevelWhateverTheGatherers() {
        assertTrue(TwoHatchMuta.shouldPlanFlyerAttack(withSpire(), 7, 0, 22));
    }

    @Test
    void theWorkerFloorStaysUnderTheDroneTargetAndTheHardCap() {
        assertEquals(22, TwoHatchMuta.laterFlyerUpgradeWorkerFloor(60, 35));
        assertEquals(22, TwoHatchMuta.laterFlyerUpgradeWorkerFloor(60, 23));
        assertEquals(21, TwoHatchMuta.laterFlyerUpgradeWorkerFloor(22, 35));
        assertEquals(14, TwoHatchMuta.laterFlyerUpgradeWorkerFloor(15, 23));
    }

    private static UnitTypeCount mutalisks(int planned, int living) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < planned; i++) {
            count.planUnit(UnitType.Zerg_Mutalisk);
        }
        for (int i = 0; i < living; i++) {
            count.addUnit(UnitType.Zerg_Mutalisk);
        }
        return count;
    }

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(TwoHatchMuta.shouldPlanOverlord(2, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 4, false));
    }

    @Test
    void withholdsTheOverlordBelowTwoSpires() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(1, 3, false));
    }

    @Test
    void requestsAMacroHatcheryWhileLarvaBoundWithASpireAtTheFloatBars() {
        assertTrue(requestsMacroHatchery(withSpire(), NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    /**
     * Game LBIDH0GO at frame 9917: one hatchery left after the natural Lair fell, no larva, and
     * 397 minerals and 214 gas, taken here as unreserved, with the Spire finished.
     */
    @Test
    void requestsAMacroHatcheryOnTheLastHatcheryWithTheNaturalLost() {
        assertTrue(requestsMacroHatchery(withSpire(), NO_LARVA, ONE_HATCHERY, 397, 214,
                NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void theRequestedPlanIsAMacroHatcheryOnTheMainTile() {
        Plan plan = BuildOrder.macroHatcheryPlan(LARVA_STARVED_FRAME, MAIN_TILE);

        assertEquals(UnitType.Zerg_Hatchery, plan.getPlannedUnit());
        assertTrue(plan.isMacroHatchery());
        assertEquals(MAIN_TILE, plan.getBuildPosition());
        assertEquals(LARVA_STARVED_FRAME, plan.getPriority());
    }

    @Test
    void doesNotRequestAMacroHatcheryBeforeASpireIsCommitted() {
        assertFalse(requestsMacroHatchery(new TechProgression(), NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    /**
     * Game LC0QF0B7 queued its macro hatchery at frame 8,026 while the Spire was still morphing,
     * reading the gas banked for the first Mutalisks as float.
     */
    @Test
    void doesNotRequestAMacroHatcheryWhileTheCommittedSpireIsStillMorphing() {
        TechProgression spireMorphing = new TechProgression();
        spireMorphing.setPlannedSpire(true);

        assertFalse(requestsMacroHatchery(spireMorphing, NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileEnemiesAreKnownAtOurBases() {
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, 1, NO_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileOneIsQueuedOrMorphing() {
        assertFalse(requestsMacroHatchery(withSpire(), NO_LARVA, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES, ONE_MACRO_HATCHERY));
    }

    @Test
    void doesNotRequestAMacroHatcheryWhileLarvaIsNotShort() {
        assertFalse(requestsMacroHatchery(withSpire(), TWO_HATCHERIES, TWO_HATCHERIES,
                LarvaBoundMacroHatchery.FLOAT_MINERALS, LarvaBoundMacroHatchery.FLOAT_GAS, NO_ENEMIES, NO_MACRO_HATCHERY));
    }

    private static boolean requestsMacroHatchery(TechProgression techProgression, int larva, int hatcheries,
                                                 int availableMinerals, int availableGas, int enemiesAtBases,
                                                 int outstandingMacroHatcheries) {
        return LarvaBoundMacroHatchery.shouldPlan(LarvaBoundMacroHatchery.isSpireReady(techProgression), larva,
                hatcheries, availableMinerals, availableGas, enemiesAtBases, outstandingMacroHatcheries);
    }

    /**
     * Game LBIDH0GO: Drone and Zergling plans queued while the Spire morphed were still waiting
     * when it finished. The Mutalisk derived once the Spire stands polls ahead of all of them.
     */
    @Test
    void queuesTheMutaliskAheadOfTheBacklogDerivedWhileTheSpireMorphed() {
        ProductionQueue queue = new ProductionQueue();
        for (int i = 0; i < BACKLOG_PLANS; i++) {
            UnitType unitType = i % 2 == 0 ? UnitType.Zerg_Drone : UnitType.Zerg_Zergling;
            queue.add(new UnitPlan(unitType, FIRST_BACKLOG_FRAME + i * BACKLOG_STEP));
        }

        List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR,
                queue.unitPlanCount(UnitType.Zerg_Mutalisk), new UnitTypeCount());

        assertEquals(1, plans.size());
        Plan mutalisk = plans.get(0);
        assertEquals(UnitType.Zerg_Mutalisk, mutalisk.getPlannedUnit());
        assertEquals(UnitPlan.ADVANCED_UNIT_PRIORITY, mutalisk.getPriority());
        queue.addAll(plans);
        assertSame(mutalisk, queue.poll());
        assertEquals(BACKLOG_PLANS, queue.size());
    }

    @Test
    void addsNoSecondMutaliskWhileOneStillWaitsInTheQueue() {
        UnitTypeCount count = new UnitTypeCount();

        List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR, 1, count);

        assertTrue(plans.isEmpty());
        assertEquals(0, count.get(UnitType.Zerg_Mutalisk));
    }

    @Test
    void chargesTheQueuedMutaliskToThePlannedCount() {
        UnitTypeCount count = new UnitTypeCount();

        TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR, 0, count);

        assertEquals(1, count.plannedCount(UnitType.Zerg_Mutalisk));
    }

    @Test
    void chargesNothingWhileTheGateWithholdsTheMutalisk() {
        UnitTypeCount count = new UnitTypeCount();

        List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR - 1, 0, count);

        assertTrue(plans.isEmpty());
        assertEquals(0, count.get(UnitType.Zerg_Mutalisk));
    }

    /**
     * Derives the build every frame against a queue that hands a larva to its head plan every
     * few frames. A scheduled Mutalisk leaves the queue and hatches, moving from the planned
     * count to the living count. Returns the number of Mutalisk plans derived.
     */
    private static int deriveWave(UnitTypeCount count, ProductionQueue queue, int target) {
        int derived = 0;
        for (int frame = 1; frame <= WAVE_FRAMES; frame++) {
            List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), target, GATHERER_FLOOR,
                    queue.unitPlanCount(UnitType.Zerg_Mutalisk), count);
            derived += plans.size();
            queue.addAll(plans);
            assertTrue(queue.unitPlanCount(UnitType.Zerg_Mutalisk) <= 1);
            assertTrue(count.get(UnitType.Zerg_Mutalisk) <= target);
            if (frame % FRAMES_PER_LARVA == 0 && !queue.isEmpty()) {
                queue.poll();
                count.unplanUnit(UnitType.Zerg_Mutalisk);
                count.addUnit(UnitType.Zerg_Mutalisk);
            }
        }
        return derived;
    }

    @Test
    void successiveWavesReachTheMutaliskTargetWithoutOverQueueing() {
        UnitTypeCount count = new UnitTypeCount();
        ProductionQueue queue = new ProductionQueue();

        assertEquals(WAVE_TARGET, deriveWave(count, queue, WAVE_TARGET));
        assertEquals(WAVE_TARGET, count.livingCount(UnitType.Zerg_Mutalisk));
        assertEquals(0, count.plannedCount(UnitType.Zerg_Mutalisk));
        assertTrue(queue.isEmpty());

        for (int i = 0; i < LOST_MUTALISKS; i++) {
            count.removeUnit(UnitType.Zerg_Mutalisk);
        }

        assertEquals(LOST_MUTALISKS, deriveWave(count, queue, WAVE_TARGET));
        assertEquals(WAVE_TARGET, count.livingCount(UnitType.Zerg_Mutalisk));

        assertEquals(FLOATING_TARGET - WAVE_TARGET, deriveWave(count, queue, FLOATING_TARGET));
        assertEquals(FLOATING_TARGET, count.livingCount(UnitType.Zerg_Mutalisk));
        assertTrue(queue.isEmpty());
    }

    @Test
    void aWaveWithNoLarvaHoldsOnePlanAndChargesOnlyThatPlan() {
        UnitTypeCount count = new UnitTypeCount();
        ProductionQueue queue = new ProductionQueue();

        for (int frame = 0; frame < WAVE_FRAMES; frame++) {
            queue.addAll(TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR,
                    queue.unitPlanCount(UnitType.Zerg_Mutalisk), count));
        }

        assertEquals(1, queue.size());
        assertEquals(1, count.get(UnitType.Zerg_Mutalisk));
    }

    private static int flyerAttackPriority(int livingMutalisks) {
        UnitTypeCount count = new UnitTypeCount();
        for (int i = 0; i < livingMutalisks; i++) {
            count.addUnit(UnitType.Zerg_Mutalisk);
        }
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return new TwoHatchMuta().upgradePriority(UpgradeType.Zerg_Flyer_Attacks, count, techProgression, 12000);
    }

    @Test
    void flyerAttacksPollsAheadOfMutalisksOnceTheMutalisksThatPlanItAreAlive() {
        assertEquals(12000, flyerAttackPriority(TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE - 1));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, flyerAttackPriority(TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE));
        assertEquals(BuildOrder.ARMY_UPGRADE_PRIORITY, flyerAttackPriority(TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE + 1));
    }

    private static final int FLOAT_BAR = 350;

    private static final int ALL_GAS = 1000;

    private static final int MUTALISK_MINERALS = UnitType.Zerg_Mutalisk.mineralPrice();

    /**
     * Game M7CHH005 frame 8,158: 352 unreserved minerals while the Spire morphed bought an
     * expansion the first Mutalisks needed the bank for.
     */
    @Test
    void floatingMineralsPlanNoExpansionWhileTheFirstWaveOwnsTheBank() {
        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, true, true, 352, ALL_GAS, FLOAT_BAR, 0));
        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, true, true, FLOAT_BAR + 7 * MUTALISK_MINERALS, ALL_GAS, FLOAT_BAR, 0));
    }

    @Test
    void aBankBeyondTheFirstWaveStillPlansAnExpansionWhileTheSpireMorphs() {
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true, FLOAT_BAR + 7 * MUTALISK_MINERALS + 1, ALL_GAS,
                FLOAT_BAR, 0));
    }

    @Test
    void theFirstWaveCostShrinksAsMutalisksAreCounted() {
        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, true, true, FLOAT_BAR + 3 * MUTALISK_MINERALS, ALL_GAS, FLOAT_BAR, 4));
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true, FLOAT_BAR + 3 * MUTALISK_MINERALS + 1, ALL_GAS,
                FLOAT_BAR, 4));
    }

    @Test
    void mineralsTheGasCannotTurnIntoMutalisksAreNotHeldBack() {
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true, 500, 100, FLOAT_BAR, 0));
        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, true, true, 500, 200, FLOAT_BAR, 0));
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true, 500, -50, FLOAT_BAR, 0));
    }

    @Test
    void theMacroHatcheryAsksOnMineralsAloneOnceTheFirstWaveIsQueuedAndUntilFourHatcheries() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;

        assertEquals(0, TwoHatchMuta.macroHatcheryGasBar(true, wave, 3));
        assertEquals(LarvaBoundMacroHatchery.FLOAT_GAS, TwoHatchMuta.macroHatcheryGasBar(true, wave - 1, 3));
        assertEquals(LarvaBoundMacroHatchery.FLOAT_GAS, TwoHatchMuta.macroHatcheryGasBar(false, wave, 3));
        assertEquals(LarvaBoundMacroHatchery.FLOAT_GAS,
                TwoHatchMuta.macroHatcheryGasBar(true, wave, TwoHatchMuta.MACRO_HATCHERY_HATCHERY_CAP));
    }

    @Test
    void theHoldIsReleasedOnceTheSeventhMutaliskIsCounted() {
        int mutalisks = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;

        assertTrue(TwoHatchMuta.ownsFirstWaveBank(true, mutalisks - 1));
        assertFalse(TwoHatchMuta.ownsFirstWaveBank(true, mutalisks));
        assertFalse(TwoHatchMuta.ownsFirstWaveBank(false, 0));
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true, FLOAT_BAR - 100, ALL_GAS, FLOAT_BAR, mutalisks));
    }

    @Test
    void floatingMineralsPlanAnExpansionBeforeASpireIsCommitted() {
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, false, 352, ALL_GAS, FLOAT_BAR, 0));
    }

    @Test
    void fallingBehindOnBasesStillPlansAnExpansionDuringTheSpireMorph() {
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(true, false, true, 0, ALL_GAS, FLOAT_BAR, 0));
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(true, true, true, 352, ALL_GAS, FLOAT_BAR, 0));
    }

    @Test
    void noFloatNoLagPlansNoExpansion() {
        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, false, false, 0, ALL_GAS, FLOAT_BAR, 0));
    }

    /**
     * Game M7CHH005 frame 8,817: 474 minerals, gas for more, and 3 larva on the Spire frame, with
     * one Mutalisk planned and a Drone and Zergling taking the other two larva.
     */
    @Test
    void queuesOneMutaliskPerLarvaOnTheSpireCompletionFrame() {
        UnitTypeCount count = new UnitTypeCount();

        List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR, 0, count, 3, 474, 362);

        assertEquals(3, plans.size());
        assertEquals(3, count.plannedCount(UnitType.Zerg_Mutalisk));
        for (Plan plan : plans) {
            assertEquals(UnitType.Zerg_Mutalisk, plan.getPlannedUnit());
            assertEquals(UnitPlan.ADVANCED_UNIT_PRIORITY, plan.getPriority());
        }
    }

    @Test
    void theFirstWaveQueuesAheadOfTheBacklogOnTheSpireCompletionFrame() {
        ProductionQueue queue = new ProductionQueue();
        for (int i = 0; i < BACKLOG_PLANS; i++) {
            UnitType unitType = i % 2 == 0 ? UnitType.Zerg_Drone : UnitType.Zerg_Zergling;
            queue.add(new UnitPlan(unitType, FIRST_BACKLOG_FRAME + i * BACKLOG_STEP));
        }

        queue.addAll(TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR, 0, new UnitTypeCount(),
                3, 474, 362));

        for (int i = 0; i < 3; i++) {
            assertEquals(UnitType.Zerg_Mutalisk, queue.poll().getPlannedUnit());
        }
        assertEquals(BACKLOG_PLANS, queue.size());
    }

    @Test
    void theFirstWaveIsBoundByTheBankAndByTheLarva() {
        assertEquals(2, TwoHatchMuta.mutalisksToQueue(0, 0, 5, 250, 500));
        assertEquals(2, TwoHatchMuta.mutalisksToQueue(0, 0, 5, 900, 250));
        assertEquals(1, TwoHatchMuta.mutalisksToQueue(0, 0, 1, 900, 500));
    }

    @Test
    void theFirstWaveQueuesOneWhenNoLarvaOrBankIsFree() {
        assertEquals(1, TwoHatchMuta.mutalisksToQueue(0, 0, 0, 0, 0));
    }

    @Test
    void theFirstWaveCountsPlansAlreadyWaiting() {
        assertEquals(2, TwoHatchMuta.mutalisksToQueue(1, 1, 3, 900, 500));
        assertEquals(0, TwoHatchMuta.mutalisksToQueue(1, 3, 3, 900, 500));
    }

    @Test
    void theFirstWaveStopsAtTheSeventhMutalisk() {
        assertEquals(2, TwoHatchMuta.mutalisksToQueue(5, 0, 5, 900, 500));
    }

    @Test
    void laterMutalisksQueueOneAtATimeWhateverTheLarva() {
        assertEquals(1, TwoHatchMuta.mutalisksToQueue(TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE, 0, 5, 900, 500));
        assertEquals(0, TwoHatchMuta.mutalisksToQueue(TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE, 1, 5, 900, 500));
    }

    @Test
    void theFirstWaveNeverExceedsTheMutaliskTarget() {
        UnitTypeCount count = new UnitTypeCount();

        List<Plan> plans = TwoHatchMuta.planMutalisk(withSpire(), 2, GATHERER_FLOOR, 0, count, 5, 900, 500);

        assertEquals(2, plans.size());
    }

    @Test
    void theFirstWaveQueuesNothingBelowTheGathererFloor() {
        assertTrue(TwoHatchMuta.planMutalisk(withSpire(), WAVE_TARGET, GATHERER_FLOOR - 1, 0, new UnitTypeCount(),
                3, 474, 362).isEmpty());
    }

    @Test
    void theReleasedMacroHatcheryIsPlannedOnceTheFirstWaveIsScheduled() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        assertTrue(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 3, 0, 0));
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave - 1, 3, 0, 0));
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(false, wave, 3, 0, 0));
    }

    @Test
    void theReleasedMacroHatcheryWaitsOnTheCapAnOutstandingMacroHatcheryAndAThreat() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, TwoHatchMuta.MACRO_HATCHERY_HATCHERY_CAP, 0, 0));
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 3, 1, 0));
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 3, 0, 1));
    }

    @Test
    void anExpansionOnItsWayCountsTowardTheCapButDoesNotBlockTheMacroHatchery() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        assertTrue(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 3, 0, 0));
        assertTrue(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 2 + 1, 0, 0));
        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, wave, 3 + 1, 0, 0));
    }

    @Test
    void sevenMutalisksIssuedAndNoneHatchedCountTowardTheFirstWaveAndReleaseTheHatchery() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        int counted = TwoHatchMuta.firstWaveMutalisks(0, wave);

        assertEquals(wave, counted);
        assertFalse(TwoHatchMuta.ownsFirstWaveBank(true, counted));
        assertTrue(TwoHatchMuta.wantsReleasedMacroHatchery(true, TwoHatchMuta.scheduledWaveMutalisks(counted, 0), 3, 0, 0));
    }

    @Test
    void aMutaliskStillQueuedDoesNotArmTheReleasedHatcheryButOneInAnEggDoes() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        int counted = TwoHatchMuta.firstWaveMutalisks(2, wave - 2);

        assertFalse(TwoHatchMuta.wantsReleasedMacroHatchery(true, TwoHatchMuta.scheduledWaveMutalisks(counted, 1), 3, 0, 0));
        assertTrue(TwoHatchMuta.wantsReleasedMacroHatchery(true, TwoHatchMuta.scheduledWaveMutalisks(counted, 0), 3, 0, 0));
    }

    @Test
    void aSixthMutaliskInAnEggStillHoldsTheBankFromFloatingExpansions() {
        int wave = TwoHatchMuta.MUTALISKS_BEFORE_FLYER_UPGRADE;
        int counted = TwoHatchMuta.firstWaveMutalisks(0, wave - 1);

        assertFalse(TwoHatchMuta.wantsBaseAdvantage(false, true, true,
                FLOAT_BAR + MUTALISK_MINERALS, ALL_GAS, FLOAT_BAR, counted));
        assertTrue(TwoHatchMuta.wantsBaseAdvantage(false, true, true,
                FLOAT_BAR + MUTALISK_MINERALS + 1, ALL_GAS, FLOAT_BAR, counted));
    }
}
