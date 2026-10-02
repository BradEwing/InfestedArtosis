package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerRetreatMemoryTest {

    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position OTHER_BUNKER = new Position(1400, 1000);

    private static Map<UnitType, Integer> squad(int zerglings, int hydralisks) {
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        if (zerglings > 0) counts.put(UnitType.Zerg_Zergling, zerglings);
        if (hydralisks > 0) counts.put(UnitType.Zerg_Hydralisk, hydralisks);
        return counts;
    }

    @Test
    void aBunkerTheSquadRetreatedFromStaysHeldBeyondTheSampleRadius() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.releaseIfGrown(squad(12, 0));
        memory.retain(Collections.singletonList(BUNKER));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void aBunkerTheSquadNeverRetreatedFromIsNotHeld() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        assertFalse(memory.holds(OTHER_BUNKER));
    }

    @Test
    void lossesDoNotReleaseTheMemory() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.releaseIfGrown(squad(9, 0));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void moreOfAnyTypeReleasesTheMemory() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.releaseIfGrown(squad(13, 0));

        assertFalse(memory.holds(BUNKER));
    }

    @Test
    void aNewTypeReleasesTheMemoryEvenWhenOthersAreLost() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.releaseIfGrown(squad(6, 2));

        assertFalse(memory.holds(BUNKER));
    }

    @Test
    void aBunkerThatIsNoLongerLivingIsForgotten() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Arrays.asList(BUNKER, OTHER_BUNKER), squad(12, 0));

        memory.retain(Collections.singletonList(OTHER_BUNKER));

        assertFalse(memory.holds(BUNKER));
        assertTrue(memory.holds(OTHER_BUNKER));
    }

    @Test
    void aRetreatThatPricedNoBunkerLeavesTheMemoryAsItWas() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.record(Collections.<Position>emptyList(), squad(12, 0));

        assertTrue(memory.holds(BUNKER));
    }

    @Test
    void aFreshRetreatReplacesTheRememberedBunkersAndTheirComposition() {
        BunkerRetreatMemory memory = new BunkerRetreatMemory();
        memory.record(Collections.singletonList(BUNKER), squad(12, 0));

        memory.record(Collections.singletonList(OTHER_BUNKER), squad(20, 0));
        memory.releaseIfGrown(squad(20, 0));

        assertFalse(memory.holds(BUNKER));
        assertTrue(memory.holds(OTHER_BUNKER));
    }

    @Test
    void anEmptySquadNeverGrew() {
        assertFalse(BunkerRetreatMemory.grew(squad(12, 0), Collections.<UnitType, Integer>emptyMap()));
    }
}
