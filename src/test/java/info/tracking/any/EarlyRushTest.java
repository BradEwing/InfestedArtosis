package info.tracking.any;

import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the fast pool arm of EarlyRush, which reads the completion stamp of the tracked unit rather than the
 * frame the drone that became the pool was seen complete.
 */
class EarlyRushTest {

    private static final Time DRONE_OBSERVED = new Time(1400);
    private static final Time DRONE_COMPLETED = new Time(1496);
    private static final Time POOL_COMPLETED_LATE = new Time(2801);
    private static final Time POOL_COMPLETED_EARLY = new Time(1, 40);

    @Test
    void droneStampIsNotReadAsAFastPool() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        ObservedUnitFixture.changeType(morphed, UnitType.Zerg_Spawning_Pool);
        morphed.markCompleted(POOL_COMPLETED_LATE);

        assertFalse(EarlyRush.fastPoolScouted(tracker));
    }

    @Test
    void poolCompletingInsideTheWindowStillScouts() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        ObservedUnitFixture.changeType(morphed, UnitType.Zerg_Spawning_Pool);
        morphed.markCompleted(POOL_COMPLETED_EARLY);

        assertTrue(EarlyRush.fastPoolScouted(tracker));
    }

    @Test
    void morphingPoolIsNotScoutedUntilItCompletes() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        ObservedUnitFixture.changeType(morphed, UnitType.Zerg_Spawning_Pool);

        assertFalse(EarlyRush.fastPoolScouted(tracker));
    }

    private static ObservedUnit completedDrone() {
        ObservedUnit drone = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, DRONE_OBSERVED);
        drone.markCompleted(DRONE_COMPLETED);
        return drone;
    }
}
