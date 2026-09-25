package strategy.buildorder.terran;

import bwapi.TilePosition;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import info.UnitTypeCount;
import macro.AdvancedUnitEligibility;
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
}
