package info.tracking;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSwarmTest {

    private static final double TOLERANCE = 1e-9;
    private static final Position S1 = new Position(1232, 3520);
    private static final DarkSwarm SWARM = new DarkSwarm(382, S1, 900);
    private static final UnitType LING = UnitType.Zerg_Zergling;

    @Test
    void theFootprintIsTheSpellUnitsBoxNotACircle() {
        assertEquals(1232 - UnitType.Spell_Dark_Swarm.dimensionLeft(), SWARM.left());
        assertEquals(1232 + UnitType.Spell_Dark_Swarm.dimensionRight(), SWARM.right());
        assertEquals(3520 - UnitType.Spell_Dark_Swarm.dimensionUp(), SWARM.top());
        assertEquals(3520 + UnitType.Spell_Dark_Swarm.dimensionDown(), SWARM.bottom());
        assertEquals(1152, SWARM.left());
        assertEquals(1311, SWARM.right());
        assertEquals(3440, SWARM.top());
        assertEquals(3599, SWARM.bottom());
    }

    @Test
    void aUnitInTheBoxCornerIsCoveredThoughItIsFartherFromTheCentreThanTheBoxHalfWidth() {
        Position corner = new Position(1310, 3598);

        assertTrue(corner.getDistance(S1) > UnitType.Spell_Dark_Swarm.dimensionLeft());
        assertTrue(SWARM.overlaps(corner, LING));
        assertEquals(0, SWARM.gap(corner, LING), TOLERANCE);
    }

    @Test
    void aUnitWhoseBoxTouchesTheFootprintEdgeIsCovered() {
        Position touching = new Position(SWARM.right() + LING.dimensionLeft(), 3520);

        assertTrue(SWARM.overlaps(touching, LING));
    }

    @Test
    void aUnitWhoseBoxIsOnePixelOutsideIsNotCovered() {
        Position outside = new Position(SWARM.right() + LING.dimensionLeft() + 1, 3520);

        assertFalse(SWARM.overlaps(outside, LING));
        assertEquals(1, SWARM.gap(outside, LING), TOLERANCE);
    }

    @Test
    void aLargerBoxIsCoveredFartherOutThanASmallOne() {
        Position position = new Position(SWARM.right() + LING.dimensionLeft() + 10, 3520);

        assertFalse(SWARM.overlaps(position, LING));
        assertTrue(SWARM.overlaps(position, UnitType.Zerg_Ultralisk));
    }

    @Test
    void theGapToADiagonalPointIsMeasuredCornerToCorner() {
        Position diagonal = new Position(SWARM.right() + 30, SWARM.bottom() + 40);

        assertEquals(50, SWARM.gap(diagonal), TOLERANCE);
        assertEquals(0, SWARM.gap(S1), TOLERANCE);
    }

    @Test
    void theTrackerHoldsExactlyTheSwarmsSightedThisFrame() {
        DarkSwarmTracker tracker = new DarkSwarmTracker();
        DarkSwarm s2 = new DarkSwarm(397, new Position(1264, 3305), 900);
        tracker.update(Arrays.asList(SWARM, s2));

        assertEquals(2, tracker.getActiveSwarmCount());
        assertEquals(900, tracker.getRemainingFrames(382));

        tracker.update(Collections.singletonList(new DarkSwarm(397, new Position(1264, 3305), 373)));

        assertEquals(1, tracker.getActiveSwarmCount());
        assertNull(tracker.getSwarm(382));
        assertEquals(0, tracker.getRemainingFrames(382));
        assertEquals(373, tracker.getRemainingFrames(397));
    }

    @Test
    void aSwarmIsOursWhenWeOwnItOrWhenOnlyWeCouldHaveCastIt() {
        assertTrue(DarkSwarmTracker.isFriendly(true, false, Race.Zerg));
        assertFalse(DarkSwarmTracker.isFriendly(false, true, Race.Zerg));
        assertTrue(DarkSwarmTracker.isFriendly(false, false, Race.Terran));
        assertTrue(DarkSwarmTracker.isFriendly(false, false, Race.Protoss));
        assertFalse(DarkSwarmTracker.isFriendly(false, false, Race.Zerg));
        assertFalse(DarkSwarmTracker.isFriendly(false, false, Race.Unknown));
    }
}
