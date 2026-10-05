package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassStateTest {

    private static ExposedTargets.Group group(Position anchor) {
        return new ExposedTargets.Group(anchor, 1, 1, Collections.emptySet());
    }

    @Test
    void anExposedTargetIsAimedAtItsAnchorAndStartsTransit() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position anchor = new Position(2000, 2000);
        state.arrive(12100);

        state.targetExposed(group(anchor), 12200);

        assertTrue(state.hasTarget());
        assertTrue(state.targetsExposed());
        assertEquals(anchor, state.targetCenter());
        assertEquals(anchor, state.getStrikePoint());
        assertEquals(AirHarassState.Phase.TRANSIT, state.getPhase());
        assertFalse(state.hasArrived());
        assertEquals(12200, state.getLastProgressFrame());
    }

    @Test
    void aBaseTargetClearsTheExposedAnchor() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group(new Position(2000, 2000)), 12000);

        state.target(null, new Position(400, 3700), 12100);

        assertFalse(state.targetsExposed());
        assertNull(state.getExposedAnchor());
        assertNull(state.getExposedGroup());
        assertFalse(state.hasTarget());
    }

    @Test
    void followingAnExposedTargetReplacesItsGroupAndAnchorButNotItsStrikePoint() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position start = new Position(2000, 2000);
        state.targetExposed(group(start), 12000);
        ExposedTargets.Group moved = group(new Position(2100, 2000));

        state.follow(moved);

        assertSame(moved, state.getExposedGroup());
        assertEquals(moved.getAnchor(), state.getExposedAnchor());
        assertEquals(start, state.getStrikePoint());
        assertTrue(state.targetsExposed());
    }

    @Test
    void anEdgeTurretIsCountedOnceNoMatterHowManyMutasTakeItOn() {
        AirHarassState state = new AirHarassState(100, 600);

        assertTrue(state.engageEdgeTurret(7));
        assertFalse(state.engageEdgeTurret(7));
        assertTrue(state.engageEdgeTurret(8));
        assertEquals(2, state.edgeTurretsEngaged());
    }
}
