package info;

import bwapi.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefensePositionTest {

    private static final Position THREATENED_BASE = new Position(3200, 3000);

    private static final Position NATURAL = new Position(2080, 656);

    private static final Position MAIN_RALLY = new Position(1138, 67);

    private static final int FOUR_ZEALOTS = 4;

    private static final int NONE = 0;

    @Test
    void aThreatenedBaseWinsOverTheNaturalAndTheRallyPoint() {
        assertEquals(THREATENED_BASE, GameState.defensePosition(THREATENED_BASE, NATURAL, MAIN_RALLY));
    }

    @Test
    void aThreatenedNaturalWinsOverTheRallyPoint() {
        assertEquals(NATURAL, GameState.defensePosition(null, NATURAL, MAIN_RALLY));
    }

    @Test
    void withNoThreatTheRallyPointIsUsed() {
        assertEquals(MAIN_RALLY, GameState.defensePosition(null, null, MAIN_RALLY));
    }

    @Test
    void aMorphingNaturalUnderAttackWinsOverTheRallyPoint() {
        Position natural = GameState.threatenedNatural(NATURAL, false, true, FOUR_ZEALOTS);

        assertEquals(NATURAL, GameState.defensePosition(null, natural, MAIN_RALLY));
    }

    @Test
    void aCompletedNaturalUnderAttackIsThreatened() {
        assertEquals(NATURAL, GameState.threatenedNatural(NATURAL, true, false, FOUR_ZEALOTS));
    }

    @Test
    void aNaturalWeHaveNotStartedIsNotDefended() {
        assertNull(GameState.threatenedNatural(NATURAL, false, false, FOUR_ZEALOTS));
    }

    @Test
    void aMorphingNaturalWithNoEnemyCombatUnitsIsNotThreatened() {
        assertNull(GameState.threatenedNatural(NATURAL, false, true, NONE));
    }
}
