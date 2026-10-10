package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.ProductionQueue;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedlingTest {

    private static final Time STALLED = new Time(9, 0);

    private static final Time EARLY = new Time(6, 0);

    private static final int NO_AIR_UNITS = 0;

    private static final int ONE_BASE_TARGET = Speedling.droneTarget(1, 1);

    private static final int THIRD_HATCHERY_TARGET = Speedling.droneTarget(2, 3);

    private static final int ARMY = Speedling.ZERGLINGS_BEFORE_EXTRA_DRONES;

    private static final ToIntFunction<UnitType> NOTHING_OBSERVED = unitType -> 0;

    private static final int POOL_COMPLETE_FRAME = 3726;

    private static List<Plan> queueOpeningZerglings(Speedling buildOrder, ProductionQueue queue) {
        List<Plan> opening = new ArrayList<>();
        for (int i = 0; i < Speedling.OPENING_ZERGLING_PLANS; i++) {
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
        assertEquals(Speedling.DRONE_TARGET_ONE_BASE, Speedling.droneTarget(1, 1));
        assertEquals(Speedling.DRONE_TARGET_ONE_BASE, Speedling.droneTarget(1, 2));
        assertEquals(Speedling.DRONE_TARGET_ONE_BASE, Speedling.droneTarget(0, 0));
        assertEquals(11, Speedling.DRONE_TARGET_ONE_BASE);
    }

    @Test
    void holdsElevenDronesAtTwoBasesAndTwoHatcheries() {
        assertEquals(Speedling.DRONE_TARGET_TWO_BASES, Speedling.droneTarget(2, 2));
        assertEquals(11, Speedling.DRONE_TARGET_TWO_BASES);
        assertEquals(Speedling.DRONE_TARGET_ONE_BASE, Speedling.droneTarget(2, 2));
    }

    @Test
    void startsExtraDronesOnlyWithTheThirdHatchery() {
        int twoHatcheries = Speedling.droneTarget(2, 2);
        int threeHatcheries = Speedling.droneTarget(2, 3);

        assertEquals(Speedling.DRONE_TARGET_TWO_BASES + Speedling.DRONES_PER_EXTRA_HATCHERY, threeHatcheries);
        assertTrue(threeHatcheries > twoHatcheries);
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, twoHatcheries, ARMY, true, false));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, twoHatcheries, ARMY, true, true));
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET, threeHatcheries, ARMY, true, false));
    }

    @Test
    void addsDronesForEachHatcheryBeyondTwo() {
        int perHatchery = Speedling.DRONES_PER_EXTRA_HATCHERY;
        int twoBases = Speedling.DRONE_TARGET_TWO_BASES;

        assertEquals(twoBases + perHatchery, Speedling.droneTarget(2, 3));
        assertEquals(twoBases + 2 * perHatchery, Speedling.droneTarget(2, 4));
        assertEquals(twoBases + 3 * perHatchery, Speedling.droneTarget(2, Speedling.MAX_HATCHERIES));
    }

    @Test
    void dropsBackToTheOneBaseTargetWhenTheNaturalIsLost() {
        assertEquals(Speedling.DRONE_TARGET_ONE_BASE, Speedling.droneTarget(1, 3));
    }

    @Test
    void aBaseStillUnderConstructionAddsNoDrones() {
        assertEquals(Speedling.droneTarget(2, 2), Speedling.droneTarget(3, 2));
    }

    @Test
    void readsHatcheriesLairsAndHivesAtTheUsableReadiness() {
        assertEquals(Readiness.USABLE, Speedling.HATCHERY_READINESS);
        assertArrayEquals(new UnitType[] {UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive},
                Speedling.HATCHERY_TYPES);
    }

    @Test
    void holdsTheTargetWhileTheThirdHatcheryIsUnderConstruction() {
        int target = targetAtTwoBases(2, 1, 0);

        assertEquals(Speedling.droneTarget(2, 2), target);
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, target, ARMY, true, false));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, target, ARMY, true, true));
    }

    @Test
    void holdsTheTargetWhileTheThirdHatcheryIsOnlyPlanned() {
        int target = targetAtTwoBases(2, 0, 1);

        assertEquals(Speedling.droneTarget(2, 2), target);
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, target, ARMY, true, true));
    }

    @Test
    void raisesTheTargetOnceTheThirdHatcheryFinishes() {
        int target = targetAtTwoBases(3, 0, 0);

        assertEquals(THIRD_HATCHERY_TARGET, target);
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET, target, ARMY, true, true));
    }

    @Test
    void raisesTheTargetOnlyForFinishedHatcheriesWhenAFourthIsUnderConstruction() {
        assertEquals(THIRD_HATCHERY_TARGET, targetAtTwoBases(3, 1, 1));
        assertEquals(Speedling.droneTarget(2, 4), targetAtTwoBases(4, 0, 0));
    }

    private static int targetAtTwoBases(int completed, int underConstruction, int planned) {
        return Speedling.droneTarget(2,
                GameState.structureCount(Speedling.HATCHERY_READINESS, completed, underConstruction, planned));
    }

    @Test
    void derivesTheDroneBelowTheTarget() {
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET - 1, ONE_BASE_TARGET, ARMY, true, false));
        assertTrue(Speedling.shouldPlanDrone(12, 13, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneOnceTheTargetIsMet() {
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, ONE_BASE_TARGET, ARMY, true, false));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET, ARMY, true, false));
        assertFalse(Speedling.shouldPlanDrone(13, 13, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneWhileTheEconomicGateIsClosed() {
        assertFalse(Speedling.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, false, false));
        assertFalse(Speedling.shouldPlanDrone(12, 13, ARMY, false, false));
    }

    @Test
    void derivesTheDroneReplacementAfterLosingTheEconomy() {
        assertTrue(Speedling.shouldPlanDrone(1, ONE_BASE_TARGET, ARMY, true, false));
    }

    @Test
    void withholdsTheDroneWhileAZerglingIsOwed() {
        assertFalse(Speedling.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, true));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET - 1, ONE_BASE_TARGET, ARMY, true, true));
        assertFalse(Speedling.shouldPlanDrone(12, 13, ARMY, true, true));
    }

    @Test
    void withholdsDronesAboveElevenUntilTheZerglingArmyStands() {
        int fewerLings = Speedling.ZERGLINGS_BEFORE_EXTRA_DRONES - 1;

        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, THIRD_HATCHERY_TARGET, fewerLings, true, false));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, THIRD_HATCHERY_TARGET, 0, true, true));
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET - 1, THIRD_HATCHERY_TARGET, 0, true, false));
    }

    @Test
    void slipsTheFirstExtraDroneInAheadOfAnOwedZergling() {
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET, THIRD_HATCHERY_TARGET, ARMY, true, true));
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET, THIRD_HATCHERY_TARGET, ARMY, false, true));
    }

    @Test
    void holdsLaterExtraDronesBehindAnOwedZergling() {
        assertFalse(Speedling.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET + 3, ARMY, true, true));
        assertTrue(Speedling.shouldPlanDrone(ONE_BASE_TARGET + 1, ONE_BASE_TARGET + 3, ARMY, true, false));
    }

    @Test
    void owesTheOpeningLarvaToAZerglingRatherThanTheDroneFloor() {
        boolean owesZergling = Speedling.shouldPlanZergling(0, true);

        assertTrue(owesZergling);
        assertFalse(Speedling.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void derivesTheDroneOnceTheZerglingQueueIsFull() {
        boolean owesZergling = Speedling.shouldPlanZergling(Speedling.MAX_QUEUED_ZERGLING_PLANS, true);

        assertFalse(owesZergling);
        assertTrue(Speedling.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void derivesTheDroneBeforeTheSpawningPoolFinishes() {
        boolean owesZergling = Speedling.shouldPlanZergling(0, false);

        assertFalse(owesZergling);
        assertTrue(Speedling.shouldPlanDrone(0, ONE_BASE_TARGET, ARMY, true, owesZergling));
    }

    @Test
    void reachesTheDroneTargetOnceTheZerglingQueueIsSaturated() {
        int queuedZerglings = Speedling.MAX_QUEUED_ZERGLING_PLANS;
        int economyDrones = 0;
        while (Speedling.shouldPlanDrone(economyDrones, THIRD_HATCHERY_TARGET, ARMY, true,
                Speedling.shouldPlanZergling(queuedZerglings, true))) {
            economyDrones++;
        }

        assertEquals(THIRD_HATCHERY_TARGET, economyDrones);
    }

    @Test
    void derivesTheZerglingWhateverTheArmySize() {
        assertTrue(Speedling.shouldPlanZergling(0, true));
        assertTrue(Speedling.shouldPlanZergling(Speedling.MAX_QUEUED_ZERGLING_PLANS - 1, true));
    }

    @Test
    void withholdsTheZerglingOnlyWhileTheQueueIsFull() {
        assertFalse(Speedling.shouldPlanZergling(Speedling.MAX_QUEUED_ZERGLING_PLANS, true));
        assertFalse(Speedling.shouldPlanZergling(Speedling.MAX_QUEUED_ZERGLING_PLANS + 1, true));
    }

    @Test
    void withholdsTheZerglingWithoutASpawningPool() {
        assertFalse(Speedling.shouldPlanZergling(0, false));
    }

    @Test
    void derivesTheSecondHatcheryWithoutWaitingOnASurplus() {
        assertTrue(Speedling.shouldPlanHatchery(0, 0));
        assertTrue(Speedling.shouldPlanHatchery(1, 0));
    }

    @Test
    void withholdsAThirdHatcheryWhileMineralsAreSpent() {
        assertFalse(Speedling.shouldPlanHatchery(Speedling.HATCHERY_TARGET, 0));
        assertFalse(Speedling.shouldPlanHatchery(Speedling.HATCHERY_TARGET,
                Speedling.SURPLUS_MINERALS - 1));
    }

    @Test
    void derivesAFurtherHatcheryOnceMineralsGoUnspent() {
        for (int total = Speedling.HATCHERY_TARGET; total < Speedling.MAX_HATCHERIES; total++) {
            assertTrue(Speedling.shouldPlanHatchery(total, Speedling.SURPLUS_MINERALS));
        }
    }

    @Test
    void withholdsTheHatcheryAtAndAboveTheCeiling() {
        assertFalse(Speedling.shouldPlanHatchery(Speedling.MAX_HATCHERIES, 5000));
        assertFalse(Speedling.shouldPlanHatchery(Speedling.MAX_HATCHERIES + 1, 5000));
    }

    @Test
    void reactsToASurplusWellBelowTheMacroFloatingBar() {
        assertTrue(Speedling.shouldPlanHatchery(Speedling.HATCHERY_TARGET, 350));
    }

    @Test
    void expandsWhileOwedAHatcheryAndShortOfTheSecondBase() {
        assertTrue(Speedling.shouldExpand(true, 0));
        assertTrue(Speedling.shouldExpand(true, Speedling.BASE_TARGET - 1));
    }

    @Test
    void withholdsTheExpansionOnceTheSecondBaseIsHeldOrReserved() {
        assertFalse(Speedling.shouldExpand(true, Speedling.BASE_TARGET));
        assertFalse(Speedling.shouldExpand(true, Speedling.BASE_TARGET + 1));
    }

    @Test
    void withholdsTheExpansionWhileNoHatcheryIsOwed() {
        assertFalse(Speedling.shouldExpand(false, 0));
    }

    @Test
    void reportsTheStallPastTheDeadlineWithAnArmyAndSpeed() {
        assertTrue(Speedling.allInStalled(STALLED, Speedling.STALL_ZERGLINGS, true));
        assertTrue(Speedling.allInStalled(STALLED, Speedling.STALL_ZERGLINGS + 1, true));
    }

    @Test
    void reportsNoStallBeforeTheDeadline() {
        assertFalse(Speedling.allInStalled(EARLY, Speedling.STALL_ZERGLINGS, true));
    }

    @Test
    void reportsNoStallExactlyOnTheDeadline() {
        assertFalse(Speedling.allInStalled(Speedling.STALL_TIME, Speedling.STALL_ZERGLINGS, true));
    }

    @Test
    void reportsNoStallWithoutAnArmyLeft() {
        assertFalse(Speedling.allInStalled(STALLED, Speedling.STALL_ZERGLINGS - 1, true));
    }

    @Test
    void reportsNoStallWhileSpeedStillOwesGas() {
        assertFalse(Speedling.allInStalled(STALLED, Speedling.STALL_ZERGLINGS, false));
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

        assertTrue(Speedling.shouldPlanStallEvolutionChamber(techProgression));
        assertTrue(BuildOrder.shouldPlanSporePrerequisite(techProgression));
    }

    @Test
    void theStallPathDegradesToTheMeleeUpgradeBehindASporeDrivenEvolutionChamber() {
        TechProgression planned = new TechProgression();
        planned.setSpawningPool(true);
        planned.setPlannedEvolutionChambers(1);

        assertFalse(Speedling.shouldPlanStallEvolutionChamber(planned));
        assertFalse(BuildOrder.shouldPlanSporePrerequisite(planned));

        TechProgression standing = new TechProgression();
        standing.setSpawningPool(true);
        standing.setEvolutionChambers(1);

        assertFalse(Speedling.shouldPlanStallEvolutionChamber(standing));
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
        assertFalse(new SpeedlingZ().needLair());
    }

    @Test
    void neverPlansLairOrHive() {
        Speedling buildOrder = new SpeedlingZ();
        assertFalse(buildOrder.needLair());
        assertFalse(buildOrder.needHive());
    }

    @Test
    void neverTransitionsOut() {
        assertFalse(new SpeedlingZ().shouldTransition(null));
    }

    @Test
    void eachVariantPlaysOnlyItsRaceAsANonOpener() {
        assertOnlyPlays(new SpeedlingT(), Race.Terran);
        assertOnlyPlays(new SpeedlingP(), Race.Protoss);
        assertOnlyPlays(new SpeedlingZ(), Race.Zerg);
    }

    private static void assertOnlyPlays(Speedling buildOrder, Race... races) {
        List<Race> played = Arrays.asList(races);
        for (Race race : Race.values()) {
            assertEquals(played.contains(race), buildOrder.playsRace(race), buildOrder.getName() + " vs " + race);
        }
        assertFalse(buildOrder.isOpener());
    }

    @Test
    void everyVariantIsRegisteredAndPlayableAgainstItsRace() {
        List<String[]> variants = Arrays.asList(new String[]{"Terran", "SpeedlingT"},
                new String[]{"Protoss", "SpeedlingP"}, new String[]{"Zerg", "SpeedlingZ"});
        for (String[] variant : variants) {
            for (int startingLocations = 2; startingLocations <= 4; startingLocations++) {
                BuildOrderFactory factory = new BuildOrderFactory(startingLocations, Race.valueOf(variant[0]));
                assertNotNull(factory.getByName(variant[1]));
                assertTrue(factory.getPlayableNonOpenerNames().contains(variant[1]), variant[1]);
            }
        }
    }

    @Test
    void theLegacyNameIsNoLongerABuildOrder() {
        assertNull(new BuildOrderFactory(4, Race.Terran).getByName("SpeedlingAllIn"));
    }

    @Test
    void theBaseAdvantageIsKeptByTheTerranAndProtossVariantsOnly() {
        assertTrue(new SpeedlingT().wantsBaseAdvantage());
        assertTrue(new SpeedlingP().wantsBaseAdvantage());
        assertFalse(new SpeedlingZ().wantsBaseAdvantage());
    }

    @Test
    void anExtraBaseIsRequestedAtParityOrOnFloatingMineralsOnceTheNaturalStands() {
        assertTrue(Speedling.wantsExtraBase(true, false, 2));
        assertTrue(Speedling.wantsExtraBase(false, true, 2));
        assertFalse(Speedling.wantsExtraBase(false, false, 2));
        assertFalse(Speedling.wantsExtraBase(true, true, 1));
    }

    @Test
    void anExtraBaseStopsAtTheBaseCapOnly() {
        assertFalse(Speedling.wantsExtraBase(true, true, Speedling.MAX_BASES));
        assertTrue(Speedling.wantsExtraBase(true, true, Speedling.MAX_BASES - 1));
        assertTrue(Speedling.wantsExtraBase(true, true, 3));
    }

    @Test
    void parityBaseWaitsForTheOpeningWave() {
        assertFalse(Speedling.parityBaseWanted(true, true, Speedling.ZERGLINGS_BEFORE_EXTRA_DRONES - 1));
        assertTrue(Speedling.parityBaseWanted(true, true, Speedling.ZERGLINGS_BEFORE_EXTRA_DRONES));
        assertFalse(Speedling.parityBaseWanted(false, true, 40));
        assertFalse(Speedling.parityBaseWanted(true, false, 40));
    }

    @Test
    void aFinishedThirdBaseRaisesTheDroneTargetByAFullBase() {
        assertEquals(Speedling.droneTarget(2, 2) + Speedling.DRONES_PER_EXTRA_BASE, Speedling.droneTarget(3, 3));
        assertEquals(Speedling.droneTarget(2, 2), Speedling.droneTarget(3, 2));
        assertEquals(Speedling.droneTarget(2, 2) + Speedling.DRONES_PER_EXTRA_HATCHERY, Speedling.droneTarget(2, 3));
        assertEquals(Speedling.droneTarget(3, 3) + Speedling.DRONES_PER_EXTRA_HATCHERY, Speedling.droneTarget(3, 4));
    }

    @Test
    void withholdsSpeedUntilTheSixthOpeningZerglingIsQueued() {
        for (int plans = 0; plans < Speedling.OPENING_ZERGLING_PLANS; plans++) {
            assertFalse(Speedling.shouldPlanSpeed(true, plans), plans + " opening plans");
        }
        assertTrue(Speedling.shouldPlanSpeed(true, Speedling.OPENING_ZERGLING_PLANS));
    }

    @Test
    void withholdsSpeedWhileTheUpgradeCannotBePlanned() {
        for (int plans = 0; plans <= Speedling.OPENING_ZERGLING_PLANS + 1; plans++) {
            assertFalse(Speedling.shouldPlanSpeed(false, plans), plans + " opening plans");
        }
    }

    /**
     * The pool completes with the Extractor already finished, so the speed and zergling branches
     * open on the same frame. Taken in the order buildPlans takes them, one plan per frame, the six
     * opening zerglings come first and Metabolic Boost sorts behind all of them.
     */
    @Test
    void queuesMetabolicBoostBehindTheSixOpeningZerglingsOnThePoolFrame() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> emitted = new ArrayList<>();
        boolean speedPlanned = false;

        for (int frame = POOL_COMPLETE_FRAME; frame < POOL_COMPLETE_FRAME + 7; frame++) {
            if (Speedling.shouldPlanSpeed(!speedPlanned, buildOrder.openingZerglingPlans())) {
                Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, frame);
                speedPlanned = true;
                queue.add(speed);
                emitted.add(speed);
            } else if (Speedling.shouldPlanZergling(queue.unitPlanCount(UnitType.Zerg_Zergling), true)) {
                Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, frame);
                buildOrder.recordOpeningZergling(zergling);
                queue.add(zergling);
                emitted.add(zergling);
            }
        }

        assertEquals(7, emitted.size());
        for (int i = 0; i < Speedling.OPENING_ZERGLING_PLANS; i++) {
            assertEquals(UnitType.Zerg_Zergling, emitted.get(i).getPlannedUnit(), "plan " + i);
        }
        Plan speed = emitted.get(6);
        assertEquals(UpgradeType.Metabolic_Boost, speed.getPlannedUpgrade());
        assertTrue(speed.getPriority() > emitted.get(5).getPriority());
        assertEquals(speed, queue.toSortedList().get(queue.size() - 1));
    }

    @Test
    void recordsOnlyTheFirstSixZerglingPlansAsTheOpening() {
        Speedling buildOrder = new SpeedlingZ();
        for (int i = 0; i < Speedling.OPENING_ZERGLING_PLANS + 3; i++) {
            buildOrder.recordOpeningZergling(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + i));
        }

        assertEquals(Speedling.OPENING_ZERGLING_PLANS, buildOrder.openingZerglingPlans());
    }

    @Test
    void holdsSpeedBeforeTheOpeningZerglingsAreQueued() {
        assertTrue(new SpeedlingZ().holdsSpeedUpgrade(new ProductionQueue()));
    }

    @Test
    void holdsSpeedWhileAnyOpeningZerglingIsStillQueued() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> opening = queueOpeningZerglings(buildOrder, queue);

        for (int i = 0; i < opening.size() - 1; i++) {
            queue.remove(opening.get(i));
            assertTrue(buildOrder.holdsSpeedUpgrade(queue), (i + 1) + " opening plans left the queue");
        }
    }

    @Test
    void releasesSpeedOnceEveryOpeningZerglingHasLeftTheQueue() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        List<Plan> opening = queueOpeningZerglings(buildOrder, queue);
        opening.forEach(queue::remove);
        queue.add(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + 100));

        assertFalse(buildOrder.holdsSpeedUpgrade(queue));
    }

    @Test
    void holdsSpeedAgainWhileARequeuedOpeningZerglingWaits() {
        Speedling buildOrder = new SpeedlingZ();
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
                if (!name.startsWith("Speedling")) {
                    assertFalse(factory.getByName(name).holdsSpeedUpgrade(null), name + " against " + race);
                }
            }
        }
    }

    private static final int INHERITED_SPEED_PRIORITY = 2826;

    private static List<Plan> queueOpeningZerglingsDeferringSpeed(Speedling buildOrder, ProductionQueue queue,
                                                                  Plan speed) {
        List<Plan> opening = new ArrayList<>();
        for (int i = 0; i < Speedling.OPENING_ZERGLING_PLANS; i++) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME + i);
            buildOrder.recordOpeningZergling(zergling);
            buildOrder.deferSpeedUpgrade(queue);
            queue.add(zergling);
            opening.add(zergling);
            List<Plan> sorted = queue.toSortedList();
            assertTrue(sorted.indexOf(speed) > sorted.indexOf(zergling), "after opening plan " + (i + 1));
        }
        return opening;
    }

    /**
     * An opener hands over with its own Metabolic Boost already queued, far ahead of the opening
     * zerglings. Each opening zergling moves it one priority behind itself, so at no frame does the
     * upgrade sort ahead of an opening zergling, and a research claim, which only reaches the plans
     * behind the upgrade in the scan, holds none of them.
     */
    @Test
    void movesAnInheritedSpeedPlanBehindTheSixthOpeningZergling() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, INHERITED_SPEED_PRIORITY);
        queue.add(speed);

        List<Plan> opening = queueOpeningZerglingsDeferringSpeed(buildOrder, queue, speed);

        assertEquals(opening.get(opening.size() - 1).getPriority() + 1, speed.getPriority());
        List<Plan> sorted = queue.toSortedList();
        assertEquals(opening, sorted.subList(0, Speedling.OPENING_ZERGLING_PLANS));
        assertEquals(speed, sorted.get(sorted.size() - 1));
    }

    @Test
    void movesASpeedPlanPulledToTheReactionPriorityBeforeTheHandOffBehindTheOpeningZerglings() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, 2);
        queue.add(speed);

        List<Plan> opening = queueOpeningZerglingsDeferringSpeed(buildOrder, queue, speed);

        assertEquals(opening.get(opening.size() - 1).getPriority() + 1, speed.getPriority());
    }

    @Test
    void neverMovesASpeedUpgradeThatIsAlreadyResearching() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        Plan researching = new UpgradePlan(UpgradeType.Metabolic_Boost, INHERITED_SPEED_PRIORITY);
        researching.setState(PlanState.BUILDING);
        queue.add(researching);
        buildOrder.recordOpeningZergling(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME));

        buildOrder.deferSpeedUpgrade(queue);

        assertEquals(INHERITED_SPEED_PRIORITY, researching.getPriority());
    }

    @Test
    void leavesASpeedPlanAlreadyBehindTheNewestOpeningZerglingInPlace() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        int laterPriority = POOL_COMPLETE_FRAME + 100;
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, laterPriority);
        queue.add(speed);
        buildOrder.recordOpeningZergling(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME));

        buildOrder.deferSpeedUpgrade(queue);

        assertEquals(laterPriority, speed.getPriority());
    }

    @Test
    void leavesSpeedAloneBeforeAnyOpeningZerglingIsRecorded() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        Plan speed = new UpgradePlan(UpgradeType.Metabolic_Boost, INHERITED_SPEED_PRIORITY);
        queue.add(speed);

        buildOrder.deferSpeedUpgrade(queue);

        assertEquals(INHERITED_SPEED_PRIORITY, speed.getPriority());
    }

    @Test
    void leavesOtherUpgradesInPlace() {
        Speedling buildOrder = new SpeedlingZ();
        ProductionQueue queue = new ProductionQueue();
        Plan melee = new UpgradePlan(UpgradeType.Zerg_Melee_Attacks, INHERITED_SPEED_PRIORITY);
        queue.add(melee);
        buildOrder.recordOpeningZergling(new UnitPlan(UnitType.Zerg_Zergling, POOL_COMPLETE_FRAME));

        buildOrder.deferSpeedUpgrade(queue);

        assertEquals(INHERITED_SPEED_PRIORITY, melee.getPriority());
    }
}
