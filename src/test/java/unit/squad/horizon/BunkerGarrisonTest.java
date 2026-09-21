package unit.squad.horizon;

import bwapi.Position;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final int MARINE_COOLDOWN = 15;
    private static final int EMPTY_EVIDENCE_FRAMES = 48;

    private final ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Time(FIRST_FRAME));
    private final ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(bunker);

    private void observeVisible(int frame, int newShots, boolean targetInRange) {
        bunker.setLastLoadedCheckFrame(frame);
        tracker.updateBunkerGarrison(null, newShots, targetInRange, frame);
    }

    private static int shotsAt(int marines, int origin, int frame) {
        int shots = 0;
        for (int marine = 0; marine < marines; marine++) {
            int sinceFirstShot = frame - origin - marine * 3;
            if (sinceFirstShot >= 0 && sinceFirstShot % MARINE_COOLDOWN == 0) {
                shots++;
            }
        }
        return shots;
    }

    private void fireStaggered(int marines, int origin, int toFrame) {
        for (int frame = origin; frame <= toFrame; frame++) {
            observeVisible(frame, shotsAt(marines, origin, frame), true);
        }
    }

    private double modifier(int frame) {
        return HorizonCombatSimulator.bunkerGarrisonModifier(bunker, frame);
    }

    private double groundStrength(int frame) {
        return HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL) * modifier(frame);
    }

    @Test
    void aVisibleBunkerThatStopsFiringStillContributesStrength() {
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, frame == VOLLEY_FRAME ? BULLETS : 0, false);
        }

        assertTrue(groundStrength(LAST_FRAME) > 0);
    }

    @Test
    void silenceWithNothingInRangeDoesNotLowerTheGarrisonEstimate() {
        for (int frame = VOLLEY_FRAME; frame <= VOLLEY_FRAME + MARINE_COOLDOWN; frame++) {
            observeVisible(frame, frame == VOLLEY_FRAME ? BULLETS : 0, false);
        }
        double afterVolley = modifier(VOLLEY_FRAME + MARINE_COOLDOWN);

        for (int frame = VOLLEY_FRAME + MARINE_COOLDOWN + 1; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, 0, false);
        }

        assertEquals(0.5, afterVolley, 1e-9);
        assertEquals(afterVolley, modifier(LAST_FRAME), 1e-9);
    }

    @Test
    void aVisibleBunkerNeverSeenFiringCountsAtFullStrength() {
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, 0, false);
        }

        assertEquals(1.0, modifier(LAST_FRAME), 1e-9);
        assertEquals(HorizonCombatSimulator.weightedGroundStrength(UnitType.Terran_Bunker, ALL_SMALL),
                groundStrength(LAST_FRAME), 1e-9);
    }

    @Test
    void aFullBunkerFiringStaggeredShotsNeverReadsBelowFull() {
        double lowest = 1.0;
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, shotsAt(4, FIRST_FRAME, frame), true);
            lowest = Math.min(lowest, modifier(frame));
        }

        assertEquals(1.0, lowest, 1e-9);
        assertEquals(1.0, modifier(LAST_FRAME), 1e-9);
    }

    @Test
    void aBunkerFiringTwoMarinesReadsHalfOnceItsFirstWindowCloses() {
        double duringFirstWindow = 1.0;
        for (int frame = FIRST_FRAME; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, shotsAt(2, FIRST_FRAME, frame), true);
            if (frame < FIRST_FRAME + MARINE_COOLDOWN) {
                duringFirstWindow = Math.min(duringFirstWindow, modifier(frame));
            }
        }

        assertEquals(1.0, duringFirstWindow, 1e-9);
        assertEquals(0.5, modifier(LAST_FRAME), 1e-9);
    }

    @Test
    void aSilentBunkerWithOurUnitInRangeReadsEmpty() {
        for (int frame = FIRST_FRAME; frame < FIRST_FRAME + EMPTY_EVIDENCE_FRAMES; frame++) {
            observeVisible(frame, 0, true);
        }
        double beforeEvidence = modifier(FIRST_FRAME + EMPTY_EVIDENCE_FRAMES - 1);
        observeVisible(FIRST_FRAME + EMPTY_EVIDENCE_FRAMES, 0, true);

        assertEquals(1.0, beforeEvidence, 1e-9);
        assertEquals(0.0, modifier(FIRST_FRAME + EMPTY_EVIDENCE_FRAMES), 1e-9);
        assertEquals(0.0, groundStrength(FIRST_FRAME + EMPTY_EVIDENCE_FRAMES), 1e-9);
    }

    @Test
    void aBunkerThatLostMarinesReadsLowerInItsNextEpisode() {
        int secondEpisode = FIRST_FRAME + 200;
        fireStaggered(4, FIRST_FRAME, FIRST_FRAME + 4 * MARINE_COOLDOWN);
        for (int frame = FIRST_FRAME + 4 * MARINE_COOLDOWN + 1; frame < secondEpisode; frame++) {
            observeVisible(frame, 0, false);
        }
        double duringSecondWindow = 1.0;
        for (int frame = secondEpisode; frame <= LAST_FRAME; frame++) {
            observeVisible(frame, shotsAt(1, secondEpisode, frame), true);
            if (frame < secondEpisode + MARINE_COOLDOWN) {
                duringSecondWindow = Math.min(duringSecondWindow, modifier(frame));
            }
        }

        assertEquals(1.0, duringSecondWindow, 1e-9);
        assertEquals(0.25, modifier(LAST_FRAME), 1e-9);
    }

    @Test
    void aBunkerProvenEmptyReadsItsGarrisonAgainOnceItFires() {
        for (int frame = FIRST_FRAME; frame <= FIRST_FRAME + EMPTY_EVIDENCE_FRAMES; frame++) {
            observeVisible(frame, 0, true);
        }
        int loaded = FIRST_FRAME + EMPTY_EVIDENCE_FRAMES + 1;
        fireStaggered(3, loaded, loaded + 2 * MARINE_COOLDOWN);

        assertEquals(0.75, modifier(loaded + 2 * MARINE_COOLDOWN), 1e-9);
    }

    @Test
    void aMarineLastSeenAgainstAVisibleBunkerReadsAsLoaded() {
        Position bunkerCentre = new Position(1000, 1000);
        Position atTheDoor = new Position(1000 + UnitType.Terran_Bunker.dimensionRight() + 8, 1000);

        assertTrue(HorizonCombatSimulator.enteredBunker(UnitType.Terran_Marine, atTheDoor,
                Collections.singletonList(bunkerCentre)));
    }

    @Test
    void aMarineLastSeenAwayFromEveryBunkerStaysInTheSample() {
        Position bunkerCentre = new Position(1000, 1000);
        Position farAway = new Position(1000 + UnitType.Terran_Bunker.dimensionRight() + 64, 1000);

        assertFalse(HorizonCombatSimulator.enteredBunker(UnitType.Terran_Marine, farAway,
                Collections.singletonList(bunkerCentre)));
        assertFalse(HorizonCombatSimulator.enteredBunker(UnitType.Terran_Marine, bunkerCentre,
                Collections.emptyList()));
    }

    @Test
    void onlyInfantryThatCanLoadIsReadAsLoaded() {
        Position bunkerCentre = new Position(1000, 1000);

        assertFalse(HorizonCombatSimulator.enteredBunker(UnitType.Terran_Vulture, bunkerCentre,
                Collections.singletonList(bunkerCentre)));
    }
}
