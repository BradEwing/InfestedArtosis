package unit.squad.horizon;

import bwapi.UnitSizeType;
import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import info.tracking.ObservedUnitTracker;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bunker's garrison estimate while it stays visible. ObservedUnitTracker.onFrame stamps the loaded check frame
 * on every frame a completed bunker is visible; it needs a live bwapi Unit, so the stamp is applied directly.
 */
class BunkerGarrisonTest {

    private static final Map<UnitSizeType, Double> ALL_SMALL = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final int FIRST_FRAME = 1000;
    private static final int VOLLEY_FRAME = 1010;
    private static final int LAST_FRAME = 1600;
    private static final int BULLETS = 2;

    private final ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Time(FIRST_FRAME));
    private final ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(bunker);

    private void observeVisible(int frame, int bullets) {
        bunker.setLastLoadedCheckFrame(frame);
        tracker.updateBunkerGarrison(null, bullets, frame);
    }

    private double groundStrength(int frame) {
        return HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL)
                * HorizonCombatSimulator.bunkerGarrisonModifier(bunker, frame);
    }

    @Test
    void aVisibleBunkerThatStopsFiringStillContributesStrength() {
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, frame == VOLLEY_FRAME ? BULLETS : 0);
        }

        assertTrue(groundStrength(LAST_FRAME) > 0);
    }

    @Test
    void silenceDoesNotLowerTheGarrisonEstimate() {
        observeVisible(VOLLEY_FRAME, BULLETS);
        double afterVolley = HorizonCombatSimulator.bunkerGarrisonModifier(bunker, VOLLEY_FRAME);

        for (int frame = VOLLEY_FRAME + 1; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, 0);
        }

        assertTrue(afterVolley > 0);
        assertEquals(afterVolley, HorizonCombatSimulator.bunkerGarrisonModifier(bunker, LAST_FRAME), 1e-9);
    }

    @Test
    void aVisibleBunkerNeverSeenFiringCountsAtFullStrength() {
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, 0);
        }

        assertEquals(1.0, HorizonCombatSimulator.bunkerGarrisonModifier(bunker, LAST_FRAME), 1e-9);
        assertEquals(HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL),
                groundStrength(LAST_FRAME), 1e-9);
    }
}
