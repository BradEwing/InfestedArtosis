package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadManagerBunkerGateTest {

    @Test
    void theGateReadsAnAdvanceIntoFightOnlyFromASquadNotAlreadyFighting() {
        assertTrue(SquadManager.bunkerGateReads(SquadStatus.RETREAT, false));
        assertTrue(SquadManager.bunkerGateReads(SquadStatus.RALLY, false));
        assertFalse(SquadManager.bunkerGateReads(SquadStatus.FIGHT, false));
    }

    @Test
    void theGateStandsDownWhileOneOfOurBasesIsThreatened() {
        assertFalse(SquadManager.bunkerGateReads(SquadStatus.RETREAT, true));
    }

    @Test
    void aSquadInFightCountsAsTheArmyAttacking() {
        Squad fighting = new GroundSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        Squad rallying = new GroundSquad();
        rallying.setStatus(SquadStatus.RALLY);

        assertTrue(SquadManager.anyGroundSquadAttacking(Arrays.asList(rallying, fighting)));
        assertFalse(SquadManager.anyGroundSquadAttacking(Collections.singletonList(rallying)));
    }

    @Test
    void aFightLockThatHoldsASquadTheGateNeverReadIsReadByTheGate() {
        assertTrue(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, false, false, false));
    }

    @Test
    void aFightLockOnASquadTheGateAlreadyReadIsNotReadAgain() {
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, false, false, true));
    }

    @Test
    void aCollapseACorneredFightAThreatenedBaseOrNoLockKeepsTheGateOutOfAFightLock() {
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, true, false, false, false));
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, true, false, false));
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, false, true, false));
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, false, false, false, false, false));
        assertFalse(SquadManager.bunkerGateReadsFightLock(SquadStatus.RALLY, true, false, false, false, false));
    }

    @Test
    void aMergeLatchesTheNewSquadWhenAnySourceWasHeldAndRetiresTheSources() {
        Set<String> held = new HashSet<>(Collections.singleton("a"));
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();

        SquadManager.carryBunkerGate(held, read, Arrays.asList("a", "b"), "merged", true);

        assertEquals(new HashSet<>(Collections.singleton("merged")), held);
    }

    @Test
    void aSplitLatchesTheChildAndKeepsTheParentLatched() {
        Set<String> held = new HashSet<>(Collections.singleton("parent"));
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();

        SquadManager.carryBunkerGate(held, read, Collections.singletonList("parent"), "child", false);

        assertEquals(new HashSet<>(Arrays.asList("parent", "child")), held);
    }

    @Test
    void aSquadFormedFromSquadsNoneHeldIsNotLatched() {
        Set<String> held = new HashSet<>();
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();

        SquadManager.carryBunkerGate(held, read, Arrays.asList("a", "b"), "merged", true);

        assertTrue(held.isEmpty());
        assertNull(read.get("merged"));
    }

    @Test
    void aSquadFormedFromASquadTheGateReadTakesTheRead() {
        Set<String> held = new HashSet<>();
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();
        read.put("a", BunkerAdvanceEntry.ADVANCE);

        SquadManager.carryBunkerGate(held, read, Arrays.asList("a", "b"), "merged", true);

        assertEquals(BunkerAdvanceEntry.ADVANCE, read.get("merged"));
        assertFalse(read.containsKey("a"));
    }

    @Test
    void aHeldSquadMergedWithASquadThatWasReadStaysUnreadSoTheGateReadsItsFightLock() {
        Set<String> held = new HashSet<>(Collections.singleton("held"));
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();
        read.put("held", BunkerAdvanceEntry.ADVANCE);
        read.put("other", BunkerAdvanceEntry.ADVANCE);

        SquadManager.carryBunkerGate(held, read, Arrays.asList("held", "other"), "merged", true);

        assertFalse(SquadManager.bunkerGateAlreadyRead(read, held, "merged"));
    }

    @Test
    void aSquadTheGateReadAndDidNotHoldIsAlreadyRead() {
        Set<String> held = new HashSet<>();
        Map<String, BunkerAdvanceEntry> read = new HashMap<>();
        read.put("squad", BunkerAdvanceEntry.ENGAGE);

        assertTrue(SquadManager.bunkerGateAlreadyRead(read, held, "squad"));
        assertFalse(SquadManager.bunkerGateAlreadyRead(read, held, "unseen"));
    }
}
