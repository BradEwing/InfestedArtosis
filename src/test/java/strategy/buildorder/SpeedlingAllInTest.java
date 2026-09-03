package strategy.buildorder;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedlingAllInTest {

    private static final Time STALLED = new Time(9, 0);

    private static final Time EARLY = new Time(6, 0);

    @Test
    void derivesTheDroneBelowTheTarget() {
        assertTrue(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET - 1, true));
    }

    @Test
    void withholdsTheDroneOnceTheTargetIsMet() {
        assertFalse(SpeedlingAllIn.shouldPlanDrone(SpeedlingAllIn.DRONE_TARGET, true));
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
    }

    @Test
    void withholdsTheZerglingWithoutASpawningPool() {
        assertFalse(SpeedlingAllIn.shouldPlanZergling(0, false));
    }

    @Test
    void derivesTheSecondHatchery() {
        assertTrue(SpeedlingAllIn.shouldPlanHatchery(1, false));
    }

    @Test
    void withholdsAThirdHatcheryWhileMineralsAreSpent() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.HATCHERY_TARGET, false));
    }

    @Test
    void derivesAThirdHatcheryWhileMineralsFloat() {
        assertTrue(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.HATCHERY_TARGET, true));
    }

    @Test
    void withholdsTheHatcheryAtTheCeiling() {
        assertFalse(SpeedlingAllIn.shouldPlanHatchery(SpeedlingAllIn.MAX_HATCHERIES, true));
    }

    @Test
    void reportsTheStallPastTheDeadlineWithAnArmyAndSpeed() {
        assertTrue(SpeedlingAllIn.allInStalled(STALLED, SpeedlingAllIn.STALL_ZERGLINGS, true));
    }

    @Test
    void reportsNoStallBeforeTheDeadline() {
        assertFalse(SpeedlingAllIn.allInStalled(EARLY, SpeedlingAllIn.STALL_ZERGLINGS, true));
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
    void playsEveryMatchupAsANonOpener() {
        SpeedlingAllIn buildOrder = new SpeedlingAllIn();
        assertTrue(buildOrder.playsRace(Race.Protoss));
        assertTrue(buildOrder.playsRace(Race.Terran));
        assertTrue(buildOrder.playsRace(Race.Zerg));
        assertFalse(buildOrder.isOpener());
    }

    @Test
    void isRegisteredAgainstEveryRace() {
        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            BuildOrderFactory factory = new BuildOrderFactory(2, race);
            assertNotNull(factory.getByName("SpeedlingAllIn"));
            assertTrue(factory.getPlayableNonOpenerNames().contains("SpeedlingAllIn"));
        }
    }
}
