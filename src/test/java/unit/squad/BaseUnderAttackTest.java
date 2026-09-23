package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseUnderAttackTest {

    private static final boolean MORPHING_NATURAL = true;

    private static final boolean HELD_BASE = false;

    @Test
    void anEnemyWorkerAtAMorphingNaturalIsNotAnAttack() {
        assertFalse(SquadManager.baseUnderAttack(MORPHING_NATURAL,
                Collections.singletonList(UnitType.Protoss_Probe)));
    }

    @Test
    void anEnemyOverlordAtAMorphingNaturalIsNotAnAttack() {
        assertFalse(SquadManager.baseUnderAttack(MORPHING_NATURAL,
                Collections.singletonList(UnitType.Zerg_Overlord)));
    }

    @Test
    void aZealotAtAMorphingNaturalIsAnAttack() {
        assertTrue(SquadManager.baseUnderAttack(MORPHING_NATURAL,
                Arrays.asList(UnitType.Protoss_Probe, UnitType.Protoss_Zealot)));
    }

    @Test
    void anEnemyWorkerAtABaseWeHoldIsStillAnAttack() {
        assertTrue(SquadManager.baseUnderAttack(HELD_BASE, Collections.singletonList(UnitType.Protoss_Probe)));
    }

    @Test
    void aBaseWithNoThreatsIsNotUnderAttack() {
        assertFalse(SquadManager.baseUnderAttack(HELD_BASE, Collections.emptyList()));
        assertFalse(SquadManager.baseUnderAttack(MORPHING_NATURAL, Collections.emptyList()));
    }
}
