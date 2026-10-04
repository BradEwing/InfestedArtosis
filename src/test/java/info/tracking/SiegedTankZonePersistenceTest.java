package info.tracking;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiegedTankZonePersistenceTest {

    private static final int FRESH_FRAMES = 240;
    private static final ObservedUnitTracker.FreshnessRule FRESH =
            (visible, lastObserved, now) -> visible || now - lastObserved <= FRESH_FRAMES;
    private static final Position SPOT = new Position(2461, 373);
    private static final int SEEN = 9000;
    private static final int LONG_AFTER = SEEN + FRESH_FRAMES * 10;

    private static ObservedUnit seenAt(UnitType type) {
        return ObservedUnitFixture.observedUnit(type, SPOT, new Time(SEEN));
    }

    @Test
    void aSiegedTankHoldsItsZoneLongAfterItWasLastSeen() {
        assertTrue(ObservedUnitTracker.holdsReachZone(seenAt(UnitType.Terran_Siege_Tank_Siege_Mode), false, FRESH,
                LONG_AFTER));
    }

    @Test
    void aSiegedTankSeenUnsiegedLosesItsZoneOnceStale() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Siege_Mode);
        ObservedUnitFixture.changeType(tank, UnitType.Terran_Siege_Tank_Tank_Mode);

        assertFalse(ObservedUnitTracker.holdsReachZone(tank, false, FRESH, LONG_AFTER));
    }

    @Test
    void anUnsiegedTankHoldsItsZoneOnlyWhileFresh() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Tank_Mode);

        assertTrue(ObservedUnitTracker.holdsReachZone(tank, false, FRESH, SEEN + FRESH_FRAMES));
        assertFalse(ObservedUnitTracker.holdsReachZone(tank, false, FRESH, SEEN + FRESH_FRAMES + 1));
    }

    @Test
    void aDeadSiegedTankHoldsNoZone() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Siege_Mode);
        tank.setDestroyedFrame(new Time(SEEN + 1));

        assertFalse(ObservedUnitTracker.holdsReachZone(tank, false, FRESH, SEEN + 2));
    }

    @Test
    void otherArmyUnitsKeepTheFreshnessRule() {
        ObservedUnit marine = seenAt(UnitType.Terran_Marine);

        assertTrue(ObservedUnitTracker.holdsReachZone(marine, true, FRESH, LONG_AFTER));
        assertFalse(ObservedUnitTracker.holdsReachZone(marine, false, FRESH, LONG_AFTER));
    }

    @Test
    void aSiegedTankSeenUnsiegingInSightIsRetypedAtOnce() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Siege_Mode);

        ObservedUnitTracker.updateUnitTypeChange(tank, UnitType.Terran_Siege_Tank_Tank_Mode, new Time(SEEN + 1));

        assertEquals(UnitType.Terran_Siege_Tank_Tank_Mode, tank.getUnitType());
        assertFalse(ObservedUnitTracker.holdsReachZone(tank, false, FRESH, LONG_AFTER));
    }

    @Test
    void aTankSeenSiegingInSightIsRetypedAtOnce() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Tank_Mode);

        ObservedUnitTracker.updateUnitTypeChange(tank, UnitType.Terran_Siege_Tank_Siege_Mode, new Time(SEEN + 1));

        assertEquals(UnitType.Terran_Siege_Tank_Siege_Mode, tank.getUnitType());
    }

    @Test
    void aSiegedTanksSpotSeenEmptyForgetsItsPosition() {
        ObservedUnit tank = seenAt(UnitType.Terran_Siege_Tank_Siege_Mode);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(tank);

        tracker.clearLastKnownLocationsAt(Collections.singleton(SPOT));

        assertNull(tank.getLastKnownLocation());
    }
}
