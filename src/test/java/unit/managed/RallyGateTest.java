package unit.managed;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RallyGateTest {

    private static final Position FLYER = new Position(1000, 1000);

    @Test
    void theGateRadiusIsThreeHundredTwentyPixels() {
        assertEquals(320, ManagedUnit.RALLY_ENEMY_AIR_RADIUS);
    }

    @Test
    void noEnemyFlyerLeavesTheGateOpen() {
        assertFalse(ManagedUnit.anyWithin(FLYER, Collections.emptyList(), ManagedUnit.RALLY_ENEMY_AIR_RADIUS));
    }

    @Test
    void anEnemyFlyerWithinTheRadiusClosesTheGate() {
        Position near = new Position(1000 + ManagedUnit.RALLY_ENEMY_AIR_RADIUS - 1, 1000);

        assertTrue(ManagedUnit.anyWithin(FLYER, Collections.singletonList(near), ManagedUnit.RALLY_ENEMY_AIR_RADIUS));
    }

    @Test
    void anEnemyFlyerExactlyAtTheRadiusClosesTheGate() {
        Position edge = new Position(1000, 1000 - ManagedUnit.RALLY_ENEMY_AIR_RADIUS);

        assertTrue(ManagedUnit.anyWithin(FLYER, Collections.singletonList(edge), ManagedUnit.RALLY_ENEMY_AIR_RADIUS));
    }

    @Test
    void onlyEnemyFlyersBeyondTheRadiusLeaveTheGateOpen() {
        Position far = new Position(1000 + ManagedUnit.RALLY_ENEMY_AIR_RADIUS + 1, 1000);
        Position farther = new Position(3000, 3000);

        assertFalse(ManagedUnit.anyWithin(FLYER, Arrays.asList(far, farther), ManagedUnit.RALLY_ENEMY_AIR_RADIUS));
    }
}
