package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldRetreatTest {

    private static final int GAP = 5;

    private static Squad holding(int since, int last) {
        Squad squad = new Squad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.getHeldRetreat().hold(since, last);
        return squad;
    }

    @Test
    void aFreshMemoryHoldsNothing() {
        HeldRetreat memory = new HeldRetreat();

        assertFalse(memory.isLive(0, GAP));
        assertEquals(-1, memory.getSinceFrame());
    }

    @Test
    void aHeldRetreatStaysLiveWithinTheGapAndNotBeyondIt() {
        HeldRetreat memory = new HeldRetreat();
        memory.hold(100, 110);

        assertTrue(memory.isLive(110 + GAP, GAP));
        assertFalse(memory.isLive(110 + GAP + 1, GAP));
    }

    @Test
    void keepRefreshesTheLastFrameWithoutMovingTheSinceFrame() {
        HeldRetreat memory = new HeldRetreat();
        memory.hold(100, 110);
        memory.keep(120);

        assertEquals(100, memory.getSinceFrame());
        assertTrue(memory.isLive(125, GAP));
    }

    @Test
    void clearDropsTheHeldRetreat() {
        HeldRetreat memory = new HeldRetreat();
        memory.hold(100, 110);
        memory.clear(112);

        assertFalse(memory.isLive(112, GAP));
    }

    @Test
    void aMergeKeepsTheLatestHeldRetreatOfItsSources() {
        Squad merged = new Squad();
        merged.inheritStateFrom(Arrays.asList(holding(100, 150), holding(130, 148), holding(-1, -1)));

        assertEquals(130, merged.getHeldRetreat().getSinceFrame());
        assertEquals(150, merged.getHeldRetreat().getLastFrame());
    }

    @Test
    void sourcesHoldingNothingLeaveTheMemoryEmpty() {
        Squad merged = new Squad();
        merged.inheritStateFrom(Collections.singletonList(holding(-1, -1)));

        assertEquals(-1, merged.getHeldRetreat().getSinceFrame());
    }
}
