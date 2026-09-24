package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.TechProgression;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedlingAllInTest {

    private static final Time STALLED = new Time(9, 0);

    private static final Time EARLY = new Time(6, 0);

    private static final int NO_AIR_UNITS = 0;

    private static final int ONE_BASE_TARGET = SpeedlingAllIn.droneTarget(1, 1);

    private static final int TWO_BASE_TARGET = SpeedlingAllIn.droneTarget(2, 2);

    private static final int ARMY = SpeedlingAllIn.ZERGLINGS_BEFORE_EXTRA_DRONES;

    private static final ToIntFunction<UnitType> NOTHING_OBSERVED = unitType -> 0;

    private static final int POOL_COMPLETE_FRAME = 3726;

    private static List<Plan> queueOpeningZerglings(SpeedlingAllIn buildOrder, ProductionQueue queue) {
        List<Plan> opening = new ArrayList<>();
        for (int i = 0; i < SpeedlingAllIn.OPENING_ZERGLING_PLANS; i++) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + i);
            buildOrder.recordOpeningZergling(zergling);
            queue.add(zergling);
            opening.add(zergling);
        }
        return opening;
    }

    private static ToIntFunction<UnitType> observed(UnitType... unitTypes) {
        Map<UnitType, Integer> counts = new HashMap<>();
        for (UnitType unitType : unitTypes) {
            counts.merge(unitType, 1, Integer::sum);
        }
        return unitType -> counts.getOrDefault(unitType, 0);
    }

    @Test
    void holdsElevenDronesBelowTwoBases() {
        assertEquals(SpeedlingAllIn.DRONE_TARGET_ONE_BASE, SpeedlingAllIn.droneTarget(1, 1));
        assertEquals(SpeedlingAllIn.DRONE_TARGET_ONE_BASE, SpeedlingAllIn.droneTarget(1, 2));
        assertEquals(SpeedlingAllIn.DRONE_TARGET_ONE_BASE, SpeedlingAllIn.droneTarget(0, 0));
        assertEquals(11, SpeedlingAllIn.DRONE_TARGET_ONE_BASE);
    }

    @Test
    void holdsTwelveDronesAtTwoBasesAndTwoHatcheries() {
        assertEquals(SpeedlingAllIn.DRONE_TARGET_TWO_BASES, SpeedlingAllIn.droneTarget(2, 2));
        assertEquals(12, SpeedlingAllIn.DRONE_TARGET_TWO_BASES);
    }

    @Test
    void addsDronesForEachStandingHatcheryBeyondTwo() {
        int perHatchery = SpeedlingAllIn.DRONES_PER_EXTRA_HATCHERY;
        int twoBases = SpeedlingAllIn.DRONE_TARGET_TWO_BASES;

        assertEquals(twoBases + perHatchery, SpeedlingAllIn.droneTarget(2, 3));
        assertEquals(twoBases + 2 * perHatchery, SpeedlingAllIn.droneTarget(2, 4));
        assertEquals(twoBases + 3 * perHatchery, SpeedlingAllIn.droneTarget(2, SpeedlingAllIn.MAX_HATCHERIES));
    }

    @Test
    void dropsBackToTheOneBaseTargetWhenTheNaturalIsLost() {
        assertEquals(SpeedlingAllIn.DRONE_TARGET_ONE_BASE, SpeedlingAllIn.droneTarget(1, 3));
    }

    @Test
    void countsFinishedAndUnderConstructionButNotPlannedHatcheries() {
        assertEquals(2, SpeedlingAllIn.standingHatcheries(1, 1, 0));
        assertEquals(1, SpeedlingAllIn.standingHatcheries(1, 0, 1));
        assertEquals(3, SpeedlingAllIn.standingHatcheries(2, 1, 2));
    }

    @Test
    void withholdsTheExtraDronesForAHatcheryThatIsOnlyPlanned() {
        int plannedOnly = SpeedlingAllIn.standingHatcheries(2, 0, 1);

        assertEquals(SpeedlingAllIn.DRONE_TARGET_TWO_BASES, SpeedlingAllIn.droneTarget(2, plannedOnly));
    }

    @Test
    void raisesTheTargetForAHatcheryUnderConstruction() {
        int underConstruction = SpeedlingAllIn.standingHatcheries(2, 1, 0);

        assertEquals(SpeedlingAllIn.DRONE_TARGET_TWO_BASES + SpeedlingAllIn.DRONES_PER_EXTRA_HATCHERY,
                SpeedlingAllIn.droneTarget(2, underConstruction));
    }

    @Test
    void derivesTheDroneBelowTheTarget() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET - 1, ONE_BASE_TARGET, ARMY, true, false));
        assertTrue(SpeedlingAllIn.shouldPlanDrone(12, 13, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneOnceTheTargetIsMet() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET, ONE_BASE_TARGET, ARMY, true, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET, ARMY, true, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(13, 13, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneWhileTheEconomicGateIsClosed() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, false, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(12, 13, ARMY, false, false));
    }

    @Test
    void derivesTheDroneReplacementAfterLosingTheEconomy() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(1, ONE_BASE_TARGET, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneWhileAZerglingIsOwed() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, true));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET - 1, ONE_BASE_TARGET, ARMY, true, true));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(12, 13, ARMY, true, true));
    }

    @Test
    void withholdsDronesAboveElevenUntilTheZerglingArmyStands() {
        int fewerLings = SpeedlingAllIn.ZERGLINGS_BEFORE_EXTRA_DRONES - 1;

        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET, TWO_BASE_TARGET, fewerLings, true, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET, TWO_BASE_TARGET, 0, true, true));
        assertTrue(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET - 1, TWO_BASE_TARGET, 0, true, false));
    }

    @Test
    void slipsTheFirstExtraDroneInAheadOfAnOwedZergling() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET, TWO_BASE_TARGET, ARMY, true, true));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET, TWO_BASE_TARGET, ARMY, false, true));
    }

    @Test
    void holdsLaterExtraDronesBehindAnOwedZergling() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET + 3, ARMY, true, true));
        assertTrue(SpeedlingAllIn.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET + 3, ARMY, true, false));
    }

    @Test
    void owesTheOpeningLarvaToAZerglingRatherThanTheDroneFloor() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(0, true);

        assertTrue(owesZergling);
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void derivesTheDroneOnceTheZerglingQueueIsFull() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS, true);

        assertFalse(owesZergling);
        assertTrue(SpeedlingAllIn.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void derivesTheDroneBeforeTheSpawningPoolFinishes() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(0, false);

        assertFalse(owesZergling);
        assertTrue(SpeedlingAllIn.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void reachesTheDroneTargetOnceTheZerglingQueueIsSaturated() {
        int queuedZerglings = SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS;
        int economyDrones = 0;
        while (SpeedlingAllIn.shouldPlanDrone(economyDrones, TWO_BASE_TARGET, ARMY, true,
                SpeedlingAllIn.shouldPlanZergling(queuedZerglings, true))) {
            economyDrones++;
        }

        assertEquals(TWO_BASE_TARGET, economyDrones);
    }

    @Test
    void derivesTheZerglingWhateverTheArmySize() {
        assertTrue(SpeedlingAllIn.shouldPlanZergling(0, true));
        assertTrue(SpeedlingAllIn.shouldPlanZergling(SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS - 1, true));
    }

    @Test
    void withholdsTheZerglingOnlyWhileTheQueueIsFull() {
        assertFalse(SpeedlingAllIn.shouldPlanZergling(SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS, true));
        assertFalse(SpeedlingAllIn.shouldPlanZergling(SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS + 1, true));
    }

    @Test
    void withholdsTheZerglingWithoutASpawningPool() {
        assertFalse(SpeedlingAllIn.shouldPlanZergling(0, false));
    }

    @Test
    void derivesTheSecondHatcheryWithoutWaitingOnASurplus() {
        assertTrue(SpeedlingAllIn.shouldPlanHatchery(0, 0));
        assertTrue(SpeedlingAllIn.shouldPlanHatchery(1, 0));
    }

    @Test
    void withholdsAThirdHatcheryWhileMineralsAreSpent() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.HATCHERY_TARGET, 0));
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.HATCHERY_TARGET,
                SpeedlingAllIn.SURPLUS_MINERALS - 1));
    }

    @Test
    void derivesAFurtherHatcheryOnceMineralsGoUnspent() {
        for (int total = SpeedlingAllIn.HATCHERY_TARGET; total < SpeedlingAllIn.MAX_HATCHERIES; total++) {
            assertTrue(SpeedlingAllIn.shouldPlanHatchery(total, SpeedlingAllIn.SURPLUS_MINERALS));
        }
    }

    @Test
    void withholdsTheHatcheryAtAndAboveTheCeiling() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.MAX_HATCHERIES, 5000));
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.MAX_HATCHERIES + 1, 5000));
    }

    @Test
    void reactsToASurplusWellBelowTheMacroFloatingBar() {
        assertTrue(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.HATCHERY_TARGET, 350));
    }

    @Test
    void expandsWhileOwedAHatcheryAndShortOfTheSecondBase() {
        assertTrue(SpeedlingAllIn.shouldExpand(true, 0));
        assertTrue(SpeedlingAllIn.shouldExpand(true, SpeedlingAllIn.BASE_TARGET - 1));
    }

    @Test
    void withholdsTheExpansionOnceTheSecondBaseIsHeldOrReserved() {
        assertFalse(SpeedlingAllIn.shouldExpand(true, SpeedlingAllIn.BASE_TARGET));
        assertFalse(SpeedlingAllIn.shouldExpand(true, SpeedlingAllIn.BASE_TARGET + 1));
    }

    @Test
    void withholdsTheExpansionWhileNoHatcheryIsOwed() {
        assertFalse(SpeedlingAllIn.shouldExpand(false, 0));
    }

    @Test
    void reportsTheStallPastTheDeadlineWithAnArmyAndSpeed() {
        assertTrue(SpeedlingAllIn.allInStalled(STALLED, SpeedlingAllIn.STALL_ZERGLINGS, true));
        assertTrue(SpeedlingAllIn.allInStalled(STALLED, SpeedlingAllIn.STALL_ZERGLINGS + 1, true));
    }

    @Test
    void reportsNoStallBeforeTheDeadline() {
        assertFalse(SpeedlingAllIn.allInStalled(EARLY, SpeedlingAllIn.STALL_ZERGLINGS, true));
    }

    @Test
    void reportsNoStallExactlyOnTheDeadline() {
        assertFalse(SpeedlingAllIn.allInStalled(SpeedlingAllIn.STALL_TIME, SpeedlingAllIn.STALL_ZERGLINGS, true));
    }

    @Test
    void reportsNoStallWithoutAnArmyLeft() {
        assertFalse(SpeedlingAllIn.allInStalled(STALLED, SpeedlingAllIn.STALL_ZERGLINGS - 1, true));
    }

    @Test
    void reportsNoStallWhileSpeedStillOwesGas() {
        assertFalse(SpeedlingAllIn.allInStalled(STALLED, SpeedlingAllIn.STALL_ZERGLINGS, false));
    }

    /**
     * The build has no matchup class, so the race dispatch is the whole of its Spore target. Each
     * race's number is the one its matchup base class asks for.
     */
    @Test
    void asksForTheMatchupSporeTargetOfEveryRace() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.sporeTarget(Race.Protoss, observed(UnitType.Protoss_Stargate), NO_AIR_UNITS));
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.sporeTarget(Race.Terran, observed(UnitType.Terran_Wraith), NO_AIR_UNITS));
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.sporeTarget(Race.Zerg, observed(UnitType.Zerg_Spire), NO_AIR_UNITS));
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.sporeTarget(Race.Zerg, NOTHING_OBSERVED, 3));
    }

    @Test
    void asksForNoSporeWhileTheOpponentRaceIsUnknown() {
        assertEquals(0, SporeTargets.sporeTarget(Race.Unknown,
                observed(UnitType.Protoss_Stargate, UnitType.Terran_Wraith, UnitType.Zerg_Spire), 3));
    }

    @Test
    void asksForNoSporeWithNoAirThreatObserved() {
        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            assertEquals(0, SporeTargets.sporeTarget(race, NOTHING_OBSERVED, NO_AIR_UNITS),
                    "should ask for no spore against " + race);
        }
    }

    /**
     * A Spore needs an Evolution Chamber, and the stall path takes one for its melee upgrade. Both
     * read chambers standing plus chambers planned, so the first to ask builds the only one.
     */
    @Test
    void theStallPathTakesTheEvolutionChamberNoOneHasTakenYet() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);

        assertTrue(SpeedlingAllIn.shouldPlanStallEvolutionChamber(techProgression));
        assertTrue(BuildOrder.shouldPlanSporePrerequisite(techProgression));
    }

    @Test
    void theStallPathDegradesToTheMeleeUpgradeBehindASporeDrivenEvolutionChamber() {
        TechProgression planned = new TechProgression();
        planned.setSpawningPool(true);
        planned.setPlannedEvolutionChambers(1);

        assertFalse(SpeedlingAllIn.shouldPlanStallEvolutionChamber(planned));
        assertFalse(BuildOrder.shouldPlanSporePrerequisite(planned));

        TechProgression standing = new TechProgression();
        standing.setSpawningPool(true);
        standing.setEvolutionChambers(1);

        assertFalse(SpeedlingAllIn.shouldPlanStallEvolutionChamber(standing));
        assertTrue(standing.canPlanMeleeUpgrades());
    }

    /**
     * Melee stops at level 1, so the only upgrade either path researches never reaches the count
     * that would pull in a Lair.
     */
    @Test
    void pullsNoLairBehindTheSharedEvolutionChamber() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpawningPool(true);
        techProgression.setEvolutionChambers(1);
        techProgression.setMeleeUpgrades(1);

        assertFalse(techProgression.needLairForNextEvolutionChamberUpgrades());
        assertFalse(new SpeedlingAllIn().needLair());
    }

    @Test
    void neverPlansLairOrHive() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        assertFalse(buildOrder.needLair());
        assertFalse(buildOrder.needHive());
    }

    @Test
    void neverTransitionsOut() {
        assertFalse(new SpeedlingAllIn().shouldTransition(null));
    }

    @Test
    void playsEveryRaceAsANonOpener() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        for (Race race : Race.values()) {
            assertTrue(buildOrder.playsRace(race), "should play " + race);
        }
        assertFalse(buildOrder.isOpener());
    }

    @Test
    void isRegisteredAgainstEveryRace() {
        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            for (int startingLocations = 2; startingLocations <= 4; startingLocations++) {
                BuildOrderFactory factory = new BuildOrderFactory(startingLocations, race);
                assertNotNull(factory.getByName("SpeedlingAllIn"));
                assertTrue(factory.getPlayableNonOpenerNames().contains("SpeedlingAllIn"));
            }
        }
    }

    @Test
    void withholdsSpeedUntilTheSixthOpeningZerglingIsQueued() {
        for (int plans = 0; plans < SpeedlingAllIn.OPENING_ZERGLING_PLANS; plans++) {
            assertFalse(SpeedlingAllIn.shouldPlanSpeed(true, plans), plans + " opening plans");
        }
        assertTrue(SpeedlingAllIn.shouldPlanSpeed(true, SpeedlingAllIn.OPENING_ZERGLING_PLANS));
    }

    @Test
    void withholdsSpeedWhileTheUpgradeCannotBePlanned() {
        for (int plans = 0; plans <= SpeedlingAllIn.OPENING_ZERGLING_PLANS + 1; plans++) {
            assertFalse(SpeedlingAllIn.shouldPlanSpeed(false, plans), plans + " opening plans");
        }
    }

    /**
     * The pool completes with the Extractor already finished, so the speed and zergling branches
     * open on the same frame. Taken in the order buildPlans takes them, one plan per frame, the six
     * opening zerglings come first and Metabolic Boost sorts behind all of them.
     */
    @Test
    void queuesMetabolicBoostBehindTheSixOpeningZerglingsOnThePoolFrame() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> emitted = new ArrayList<>();
        boolean speedPlanned = false;

        for (int frame = POOL_COMPLETE_FRAME; frame < POOL_COMPLETE_FRAME + 7; frame++) {
            if (SpeedlingAllIn.shouldPlanSpeed(!speedPlanned, buildOrder.openingZerglingPlans())) {
                Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, frame);
                speedPlanned = true;
                queue.add(speed);
                emitted.add(speed);
            } else if (SpeedlingAllIn.shouldPlanZergling(queue.unitPlanCount(UnitType.Zerg_Zergling), true)) {
                Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, frame);
                buildOrder.recordOpeningZergling(zergling);
                queue.add(zergling);
                emitted.add(zergling);
            }
        }

        assertEquals(7, emitted.size());
        for (int i = 0; i < SpeedlingAllIn.OPENING_ZERGLING_PLANS; i++) {
            assertEquals(UnitType.Zerg_Zergling, emitted.get(i).getPlannedUnit(), "plan " + i);
        }
        Plan speed = emitted.get(6);
        assertEquals(UpgradeType.Metabolic_Boost, speed.getPlannedUpgrade());
        assertTrue(speed.getPriority() > emitted.get(5).getPriority());
        assertEquals(speed, queue.toSortedList().get(queue.size() - 1));
    }

    @Test
    void recordsOnlyTheFirstSixZerglingPlansAsTheOpening() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        for (int i = 0; i < SpeedlingAllIn.OPENING_ZERGLING_PLANS + 3; i++) {
            buildOrder.recordOpeningZergling(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + i));
        }

        assertEquals(SpeedlingAllIn.OPENING_ZERGLING_PLANS, buildOrder.openingZerglingPlans());
    }

    @Test
    void holdsSpeedBeforeTheOpeningZerglingsAreQueued() {
        assertTrue(new SpeedlingAllIn().holdsSpeedUpgrade(new ProductionQueue()));
    }

    @Test
    void holdsSpeedWhileAnyOpeningZerglingIsStillQueued() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> opening = queueOpeningZerglings(buildOrder, queue);

        for (int i = 0; i < opening.size() - 1; i++) {
            queue.remove(opening.get(i));
            assertTrue(buildOrder.holdsSpeedUpgrade(queue), (i + 1) + " opening plans left the queue");
        }
    }

    @Test
    void releasesSpeedOnceEveryOpeningZerglingHasLeftTheQueue() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> opening = queueOpeningZerglings(buildOrder, queue);
        opening.forEach(queue::remove);
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + 100));

        assertFalse(buildOrder.holdsSpeedUpgrade(queue));
    }

    @Test
    void holdsSpeedAgainWhileARequeuedOpeningZerglingWaits() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> opening = queueOpeningZerglings(buildOrder, queue);
        opening.forEach(queue::remove);
        queue.add(opening.get(0));

        assertTrue(buildOrder.holdsSpeedUpgrade(queue));
    }

    @Test
    void noOtherBuildOrderHoldsSpeed() {
        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg, Race.Unknown}) {
            BuildOrderFactory factory = new BuildOrderFactory(4, race);
            for (String name : factory.getAllBuildOrderNames()) {
                if (!"SpeedlingAllIn".equals(name)) {
                    assertFalse(factory.getByName(name).holdsSpeedUpgrade(null), name + " against " + race);
                }
            }
        }
    }
}
