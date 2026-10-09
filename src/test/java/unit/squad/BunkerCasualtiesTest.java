package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerCasualtiesTest {

    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position OTHER_BUNKER = new Position(3000, 1000);
    private static final int BUNKER_ID = 7;
    private static final int FRAME = 8000;

    private static List<BunkerAdvanceGate.Bunker> living() {
        return Arrays.asList(new BunkerAdvanceGate.Bunker(BUNKER_ID, BUNKER, 350),
                new BunkerAdvanceGate.Bunker(8, OTHER_BUNKER, 350));
    }

    @Test
    void aDeathWithinTheRadiusCountsAtTheNearestBunker() {
        BunkerCasualties casualties = new BunkerCasualties();

        casualties.recordDeath(new Position(1000 + BunkerCasualties.RADIUS, 1000), Arrays.asList(BUNKER, OTHER_BUNKER),
                FRAME);

        assertEquals(1, casualties.lostSince(BUNKER, 0));
        assertEquals(0, casualties.lostSince(OTHER_BUNKER, 0));
    }

    @Test
    void aDeathBeyondTheRadiusCountsAtNoBunker() {
        BunkerCasualties casualties = new BunkerCasualties();

        casualties.recordDeath(new Position(1000 + BunkerCasualties.RADIUS + 1, 1000),
                Collections.singletonList(BUNKER), FRAME);

        assertEquals(0, casualties.lostSince(BUNKER, 0));
    }

    @Test
    void onlyDeathsOnOrAfterTheFrameCount() {
        BunkerCasualties casualties = new BunkerCasualties();
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), FRAME);
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), FRAME + 100);

        assertEquals(2, casualties.lostSince(BUNKER, FRAME));
        assertEquals(1, casualties.lostSince(BUNKER, FRAME + 1));
    }

    @Test
    void aRetreatOnSightWithNothingLostBooksNoLoss() {
        BunkerCasualties casualties = new BunkerCasualties();

        Map<BunkerAdvanceGate.Bunker, Integer> lost = BunkerAdvanceGate.lostAt(living(),
                Collections.singletonList(BUNKER), Collections.emptyList(), casualties, FRAME);

        assertTrue(lost.isEmpty());
    }

    @Test
    void aRetreatAfterUnitsDiedAtThePricedBunkerBooksTheLossWithTheUnitsLost() {
        BunkerCasualties casualties = new BunkerCasualties();
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), FRAME - 100);
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), FRAME - 50);

        Map<BunkerAdvanceGate.Bunker, Integer> lost = BunkerAdvanceGate.lostAt(living(),
                Arrays.asList(BUNKER, OTHER_BUNKER), Collections.emptyList(), casualties, FRAME);

        assertEquals(1, lost.size());
        assertEquals(BUNKER_ID, lost.keySet().iterator().next().getId());
        assertEquals(2, lost.values().iterator().next());
    }

    @Test
    void deathsAtAnotherBunkerDoNotBookTheLossAtThePricedOne() {
        BunkerCasualties casualties = new BunkerCasualties();
        casualties.recordDeath(OTHER_BUNKER, Collections.singletonList(OTHER_BUNKER), FRAME - 50);

        Map<BunkerAdvanceGate.Bunker, Integer> lost = BunkerAdvanceGate.lostAt(living(),
                Collections.singletonList(BUNKER), Collections.emptyList(), casualties, FRAME);

        assertTrue(lost.isEmpty());
    }

    @Test
    void deathsOlderThanTheHoldTimeoutBookNoLoss() {
        BunkerCasualties casualties = new BunkerCasualties();
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), 100);

        Map<BunkerAdvanceGate.Bunker, Integer> lost = BunkerAdvanceGate.lostAt(living(),
                Collections.singletonList(BUNKER), Collections.emptyList(), casualties,
                100 + BunkerAdvanceGate.HOLD_TIMEOUT_FRAMES + 1);

        assertTrue(lost.isEmpty());
    }

    @Test
    void aBunkerAtOneOfOurBasesIsNeverBooked() {
        BunkerCasualties casualties = new BunkerCasualties();
        casualties.recordDeath(BUNKER, Collections.singletonList(BUNKER), FRAME - 50);

        Map<BunkerAdvanceGate.Bunker, Integer> lost = BunkerAdvanceGate.lostAt(living(),
                Collections.singletonList(BUNKER), Collections.singletonList(new Position(900, 1000)), casualties,
                FRAME);

        assertTrue(lost.isEmpty());
    }
}
