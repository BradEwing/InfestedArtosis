package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassStateTest {

    @Test
    void anExposedTargetIsAimedAtItsAnchorAndStartsTransit() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position anchor = new Position(2000, 2000);
        state.arrive(12100);

        state.targetExposed(anchor, 12200);

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
        state.targetExposed(new Position(2000, 2000), 12000);

        state.target(null, new Position(400, 3700), 12100);

        assertFalse(state.targetsExposed());
        assertNull(state.getExposedAnchor());
        assertFalse(state.hasTarget());
    }
}
