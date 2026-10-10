package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadManagerBunkerGateTest {

    private static final Position X = new Position(1000, 1000);
    private static final Position Y = new Position(1500, 1000);

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
        reads.record("a", BunkerAdvanceEntry.ADVANCE, 100, Collections.singletonList(X), true);

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("a", "b"), "merged", true);

        assertEquals(BunkerAdvanceEntry.ADVANCE, reads.get("merged").getEntry());
        assertEquals(100, reads.get("merged").getFrame());
        assertNull(reads.get("a"));
    }

    @Test
    void aMergeTakesTheLatestReadAndTheLatestWeighingOfEachBunker() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("a", BunkerAdvanceEntry.ADVANCE, 100, Arrays.asList(X, Y), true);
        reads.record("b", BunkerAdvanceEntry.FIGHT_LOCK, 300, Collections.singletonList(X), true);

        SquadManager.carryBunkerGate(new HashSet<>(), reads, Arrays.asList("a", "b"), "merged", true);

        assertEquals(300, reads.get("merged").getFrame());
        assertEquals(BunkerAdvanceEntry.FIGHT_LOCK, reads.get("merged").getEntry());
        assertTrue(reads.readSince("merged", losses(X, 250), true));
        assertFalse(reads.readSince("merged", losses(Y, 250), true));
        assertTrue(reads.readSince("merged", losses(Y, 50), true));
    }

    @Test
    void aHeldSquadMergedWithASquadThatWasReadStaysUnreadSoTheGateReadsItsFightLock() {
        Set<String> held = new HashSet<>(Collections.singleton("held"));
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("held", BunkerAdvanceEntry.ADVANCE, 100, Collections.singletonList(X), true);
        reads.record("other", BunkerAdvanceEntry.ADVANCE, 100, Collections.singletonList(X), true);

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("held", "other"), "merged", true);

        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "merged", Collections.emptyMap(), true));
    }

    @Test
    void aSquadTheGateReadAndDidNotHoldIsAlreadyReadWhileNoLossIsOnRecordAfterTheRead() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ENGAGE, 100, Collections.singletonList(X), true);

        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "squad", Collections.emptyMap(), true));
        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "squad", losses(X, 99), true));
        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "unseen", Collections.emptyMap(), true));
    }

    @Test
    void aSquadReadNoLossThenAMergedChildFightLockedTowardABunkerWithALaterLossIsGated() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("parent", BunkerAdvanceEntry.ADVANCE, 100, Collections.emptyList(), true);

        SquadManager.carryBunkerGate(held, reads, Arrays.asList("parent", "other"), "child", true);
        boolean alreadyRead = SquadManager.bunkerGateAlreadyRead(reads, held, "child", losses(X, 200), true);

        assertFalse(alreadyRead);
        assertTrue(SquadManager.bunkerGateReadsFightLock(SquadStatus.FIGHT, true, false, false, false, alreadyRead));
    }

    @Test
    void aSplitChildOfASquadReadBeforeALossIsGatedAndOneReadAfterItIsNot() {
        Set<String> held = new HashSet<>();
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("parent", BunkerAdvanceEntry.FIGHT_LOCK, 300, Collections.singletonList(X), true);

        SquadManager.carryBunkerGate(held, reads, Collections.singletonList("parent"), "child", false);

        assertFalse(SquadManager.bunkerGateAlreadyRead(reads, held, "child", losses(X, 400), true));
        assertTrue(SquadManager.bunkerGateAlreadyRead(reads, held, "child", losses(X, 200), true));
    }

    @Test
    void aReadNearOneBunkerDoesNotStandForALossAtAnotherItNeverWeighed() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ADVANCE, 500, Collections.singletonList(X), true);

        assertFalse(reads.readSince("squad", losses(Y, 100), true));
    }

    @Test
    void aReadThatWeighedASquadAsNotMostlyMeleeDoesNotStandForTheSquadOnceItIsMostlyMelee() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ADVANCE, 500, Collections.singletonList(X), false);

        assertTrue(reads.readSince("squad", losses(X, 100), false));
        assertFalse(reads.readSince("squad", losses(X, 100), true));
    }

    @Test
    void aReadInTheSameFrameAsALossDoesNotStandForItButTheTelemetryCountsIt() {
        BunkerGateReads reads = new BunkerGateReads();
        reads.record("squad", BunkerAdvanceEntry.ADVANCE, 500, Collections.singletonList(X), true);

        assertFalse(reads.readSince("squad", losses(X, 500), true));
        assertTrue(reads.readSince("squad", losses(X, 499), true));
        assertTrue(reads.weighedAtOrAfter("squad", X, 500, true));
        assertFalse(reads.weighedAtOrAfter("squad", X, 501, true));
    }

    @Test
    void theLedgerReportsTheLossFramesNearAPositionAndAtABunker() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        Position far = new Position(9000, 9000);
        ledger.record(X, 1, 300, 350, 100, 1.0);
        ledger.record(far, 2, 900, 350, 100, 1.0);

        assertEquals(losses(X, 300),
                ledger.framesNear(new Position(800, 1000), BunkerAdvanceGate.RELEVANT_RANGE));
        assertTrue(ledger.framesNear(new Position(5000, 5000), BunkerAdvanceGate.RELEVANT_RANGE).isEmpty());
        assertEquals(300, ledger.frameAt(X));
        assertEquals(BunkerLossLedger.NONE, ledger.frameAt(new Position(1, 1)));
    }

    @Test
    void aRepeatedRetreatAtABunkerMovesItsLossFrameForward() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        ledger.record(X, 1, 300, 350, 100, 1.0);
        ledger.record(X, 1, 700, 350, 100, 1.0);

        assertEquals(700, ledger.frameAt(X));
    }

    private static Map<Position, Integer> losses(Position bunker, int frame) {
        return Collections.singletonMap(bunker, frame);
    }
}
