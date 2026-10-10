package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

        SquadManager.carryBunkerGate(held, new BunkerGateReads(), Arrays.asList("a", "b"), "merged", true);

        assertEquals(new HashSet<>(Collections.singleton("merged")), held);
    }

    @Test
    void aSplitLatchesTheChildAndKeepsTheParentLatched() {
        Set<String> held = new HashSet<>(Collections.singleton("parent"));

        SquadManager.carryBunkerGate(held, new BunkerGateReads(), Collections.singletonList("parent"), "child",
                false);

        assertEquals(new HashSet<>(Arrays.asList("parent", "child")), held);
    }

    @Test
    void aSquadFormedFromSquadsNoneHeldIsNotLatched() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("a", "b"), "merged", true);

        assertTrue(held.isEmpty());
        assertNull(reads.get("merged"));
    }

    @Test
    void aSquadFormedFromASquadTheGateReadTakesTheRead() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("a", BunkerAdvanceEntry.ADVANCE, 100);

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("a", "b"), "merged", true);

        assertEquals(BunkerAdvanceEntry.ADVANCE, reads.get("merged").getEntry());
        assertEquals(100, reads.get("merged").getFrame());
        assertNull(reads.get("a"));
    }

    @Test
    void aMergeTakesTheLatestReadOfItsSources() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("a", BunkerAdvanceEntry.ADVANCE, 100);
        reads.record("b", BunkerAdvanceEntry.FIGHT_LOCK, 300);

        SquadManager.carryBunkerGate(new HashSet<>(), reads, Arrays.asList("a", "b"), "merged", true);

        assertEquals(300, reads.get("merged").getFrame());
        assertEquals(BunkerAdvanceEntry.FIGHT_LOCK, reads.get("merged").getEntry());
    }

    @Test
    void aHeldSquadMergedWithASquadThatWasReadStaysUnreadSoTheGateReadsItsFightLock() {
        Set<String> held = new HashSet<>(Collections.singleton("held"));
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("held", BunkerAdvanceEntry.ADVANCE, 100);
        reads.record("other", BunkerAdvanceEntry.ADVANCE, 100);

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("held", "other"), "merged", true);

        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "merged", BunkerLossLedger.NONE));
    }

    @Test
    void aSquadTheGateReadAndDidNotHoldIsAlreadyReadWhileNoLossIsOnRecordAfterTheRead() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ENGAGE, 100);

        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "squad", BunkerLossLedger.NONE));
        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "squad", 99));
        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "unseen", BunkerLossLedger.NONE));
    }

    @Test
    void aSquadReadNoLossThenAMergedChildFightLockedTowardABunkerWithALaterLossIsGated() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("parent", BunkerAdvanceEntry.ADVANCE, 100);
        int lossFrame = 200;

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("parent", "other"), "child", true);

        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "child", lossFrame));
        assertTrue(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, false, false,
                SquadManager.bunkerGateAlreadyRead(reads, held, "child", lossFrame)));
    }

    @Test
    void aSplitChildOfASquadReadBeforeALossIsGatedAndOneReadAfterItIsNot() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("parent", BunkerAdvanceEntry.FIGHT_LOCK, 300);

        SquadManager.carryBunkerGate(held, reads, Collections.singletonList("parent"), "child", false);

        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "child", 400));
        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "child", 200));
    }

    @Test
    void aReadInTheSameFrameAsALossDoesNotStandForIt() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ADVANCE, 500);

        assertFalse(reads.readSince("squad", 500));
        assertTrue(reads.readSince("squad", 499));
    }

    @Test
    void theLedgerReportsTheLatestLossFrameNearAPositionAndAtABunker() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        Position near = new Position(1000, 1000);
        Position far = new Position(9000, 9000);
        ledger.record(near, 1, 300, 350, 100, 1.0);
        ledger.record(far, 2, 900, 350, 100, 1.0);

        assertEquals(300, ledger.latestFrame(new Position(800, 1000), BunkerAdvanceGate.RELEVANT_RANGE));
        assertEquals(BunkerLossLedger.NONE,
                ledger.latestFrame(new Position(5000, 5000), BunkerAdvanceGate.RELEVANT_RANGE));
        assertEquals(300, ledger.frameAt(near));
        assertEquals(BunkerLossLedger.NONE, ledger.frameAt(new Position(1, 1)));
    }

    @Test
    void aRepeatedRetreatAtABunkerMovesItsLossFrameForward() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        Position bunker = new Position(1000, 1000);
        ledger.record(bunker, 1, 300, 350, 100, 1.0);
        ledger.record(bunker, 1, 700, 350, 100, 1.0);

        assertEquals(700, ledger.frameAt(bunker));
    }
}
