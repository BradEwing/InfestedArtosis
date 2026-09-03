package strategy.buildorder;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.SpeedlingAllIn.StallStep;
import strategy.buildorder.SpeedlingAllIn.Step;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedlingAllInTest {

    private static final Time STALLED = new Time(9, 0);

    private static final Time EARLY = new Time(6, 0);

    private static boolean[] gatesFor(Step... openSteps) {
        boolean[] gates = new boolean[SpeedlingAllIn.STEP_COUNT];
        for (Step step : openSteps) {
            if (step != Step.NONE) {
                gates[step.ordinal()] = true;
            }
        }
        return gates;
    }

    @Test
    void derivesTheDroneBelowTheTarget() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET - 1, true));
    }

    @Test
    void withholdsTheDroneOnceTheTargetIsMet() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET, true));
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET + 1, true));
    }

    @Test
    void withholdsTheDroneWhileTheEconomicGateIsClosed() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(0, false));
    }

    @Test
    void derivesTheDroneReplacementAfterLosingTheEconomy() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(1, true));
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

    @Test
    void takesTheSpawningPoolAheadOfEverythingElse() {
        assertEquals(Step.SPAWNING_POOL, SpeedlingAllIn.nextStep(gatesFor(Step.values())));
    }

    @Test
    void takesTheHatcheryAheadOfGasAndArmy() {
        assertEquals(Step.MACRO_HATCHERY,
                SpeedlingAllIn.nextStep(gatesFor(Step.MACRO_HATCHERY, Step.EXTRACTOR, Step.ZERGLING)));
    }

    @Test
    void takesGasAheadOfSpeed() {
        assertEquals(Step.EXTRACTOR,
                SpeedlingAllIn.nextStep(gatesFor(Step.EXTRACTOR, Step.METABOLIC_BOOST, Step.ZERGLING)));
    }

    @Test
    void takesSpeedAheadOfDrones() {
        assertEquals(Step.METABOLIC_BOOST,
                SpeedlingAllIn.nextStep(gatesFor(Step.METABOLIC_BOOST, Step.DRONE, Step.ZERGLING)));
    }

    @Test
    void replacesDronesAheadOfStallUpgradesAndArmy() {
        assertEquals(Step.DRONE,
                SpeedlingAllIn.nextStep(gatesFor(Step.DRONE, Step.STALL_UPGRADE, Step.ZERGLING)));
    }

    @Test
    void takesStallUpgradesAheadOfArmy() {
        assertEquals(Step.STALL_UPGRADE,
                SpeedlingAllIn.nextStep(gatesFor(Step.STALL_UPGRADE, Step.ZERGLING)));
    }

    @Test
    void fallsBackToTheZerglingWhenNothingElseIsOwed() {
        assertEquals(Step.ZERGLING, SpeedlingAllIn.nextStep(gatesFor(Step.ZERGLING)));
    }

    @Test
    void ownsNoStepWhenEveryGateIsClosed() {
        assertEquals(Step.NONE, SpeedlingAllIn.nextStep(gatesFor()));
    }

    @Test
    void fallsThroughToTheNextStepWhenAStepProducesNothing() {
        boolean[] gates = gatesFor(Step.MACRO_HATCHERY, Step.ZERGLING);
        assertEquals(Step.MACRO_HATCHERY, SpeedlingAllIn.nextStep(gates));
        gates[Step.MACRO_HATCHERY.ordinal()] = false;
        assertEquals(Step.ZERGLING, SpeedlingAllIn.nextStep(gates));
    }

    @Test
    void takesTheEvolutionChamberBeforeMeleeAttacks() {
        assertEquals(StallStep.EVOLUTION_CHAMBER, SpeedlingAllIn.nextStallStep(true, true));
    }

    @Test
    void takesMeleeAttacksOnceTheEvolutionChamberStands() {
        assertEquals(StallStep.MELEE_ATTACKS, SpeedlingAllIn.nextStallStep(false, true));
    }

    @Test
    void ownsNoStallStepOnceMeleeAttacksAreUnderway() {
        assertEquals(StallStep.NONE, SpeedlingAllIn.nextStallStep(false, false));
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
