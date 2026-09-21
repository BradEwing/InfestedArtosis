package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import info.TechProgression;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import util.Time;

import java.util.HashMap;
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

    private static final ToIntFunction<UnitType> NOTHING_OBSERVED = unitType -> 0;

    private static ToIntFunction<UnitType> observed(UnitType... unitTypes) {
        Map<UnitType, Integer> counts = new HashMap<>();
        for (UnitType unitType : unitTypes) {
            counts.merge(unitType, 1, Integer::sum);
        }
        return unitType -> counts.getOrDefault(unitType, 0);
    }

    @Test
    void derivesTheDroneBelowTheTarget() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET - 1, true, false));
    }

    @Test
    void withholdsTheDroneOnceTheTargetIsMet() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET, true, false));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET + 1, true, false));
    }

    @Test
    void withholdsTheDroneWhileTheEconomicGateIsClosed() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, false, false));
    }

    @Test
    void derivesTheDroneReplacementAfterLosingTheEconomy() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(1, true, false));
    }

    @Test
    void withholdsTheDroneWhileAZerglingIsOwed() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, true, true));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET - 1, true, true));
    }

    @Test
    void owesTheOpeningLarvaToAZerglingRatherThanTheDroneFloor() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(0, true);

        assertTrue(owesZergling);
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, true, owesZergling));
    }

    @Test
    void derivesTheDroneOnceTheZerglingQueueIsFull() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS, true);

        assertFalse(owesZergling);
        assertTrue(SpeedlingAllIn.shouldPlanDrone(0, true, owesZergling));
    }

    @Test
    void derivesTheDroneBeforeTheSpawningPoolFinishes() {
        boolean owesZergling = SpeedlingAllIn.shouldPlanZergling(0, false);

        assertFalse(owesZergling);
        assertTrue(SpeedlingAllIn.shouldPlanDrone(0, true, owesZergling));
    }

    @Test
    void reachesTheDroneTargetOnceTheZerglingQueueIsSaturated() {
        int queuedZerglings = SpeedlingAllIn.MAX_QUEUED_ZERGLING_PLANS;
        int economyDrones = 0;
        while (SpeedlingAllIn.shouldPlanDrone(economyDrones, true,
                SpeedlingAllIn.shouldPlanZergling(queuedZerglings, true))) {
            economyDrones++;
        }

        assertEquals(SpeedlingAllIn.DRONE_TARGET, economyDrones);
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
}
