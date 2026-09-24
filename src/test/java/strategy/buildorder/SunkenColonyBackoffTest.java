package strategy.buildorder;

import info.ColonyBuilderBackoff;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * IA-406: the sunken pair loop serves the bases the colony backoff leaves open, the way
 * GameState#basesNeedingSunken hands them to planSunkenColony.
 */
class SunkenColonyBackoffTest {

    private static final int LOST_AT = 4421;

    private static final int NEAR_TILE_X = 40;

    private static final int NEAR_TILE_Y = 110;

    private static final ToLongFunction<String> RANK = base -> BuildOrder.sunkenBaseRank("main".equals(base),
            "natural".equals(base) ? NEAR_TILE_X : NEAR_TILE_X + 1, NEAR_TILE_Y);

    private final ColonyBuilderBackoff<String> backoff = new ColonyBuilderBackoff<>();

    private Optional<String> nextPair(Set<String> needing, int frame) {
        return BuildOrder.nextSunkenBase(backoff.openBases(needing, frame, base -> 0), new HashSet<>(), RANK);
    }

    /** AC1: the base that lost the builder is served again once its hold expires. */
    @Test
    void theHeldBaseGetsNoPairUntilItsHoldExpires() {
        Set<String> needing = new HashSet<>(Arrays.asList("natural"));
        int until = backoff.recordLoss("natural", LOST_AT);

        assertFalse(nextPair(needing, LOST_AT + 1).isPresent());
        assertFalse(nextPair(needing, until - 1).isPresent());
        assertEquals("natural", nextPair(needing, until).get());
    }

    /** AC3: with the natural held, the pair goes to the other base that needs one. */
    @Test
    void thePairGoesToAnotherBaseWhileTheFirstIsHeld() {
        Set<String> needing = new HashSet<>(Arrays.asList("natural", "third"));

        assertEquals("natural", nextPair(needing, LOST_AT).get());

        backoff.recordLoss("natural", LOST_AT);

        assertEquals("third", nextPair(needing, LOST_AT + 1).get());
    }

    /** Scope 3.2: every eligible base held means no pair at all, so nothing is reserved. */
    @Test
    void noPairIsPlannedWhileEveryBaseIsHeld() {
        Set<String> needing = new HashSet<>(Arrays.asList("natural", "third"));
        backoff.recordLoss("natural", LOST_AT);
        backoff.recordLoss("third", LOST_AT);

        assertFalse(nextPair(needing, LOST_AT + 1).isPresent());
    }
}
