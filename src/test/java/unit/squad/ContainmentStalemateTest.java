package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentStalemate.COMMIT_SUPPLY_USED;
import static unit.squad.ContainmentStalemate.CommitChange;
import static unit.squad.ContainmentStalemate.HOLD_FRAMES;
import static unit.squad.ContainmentStalemate.STALEMATE_AFTER_REENTRIES;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.escalatedVerdict;
import static unit.squad.SquadManager.stalemateVerdict;

class ContainmentStalemateTest {

    private static final boolean STATIC_ONLY = true;
    private static final boolean ARMY_OUTSIDE = false;
    private static final boolean UNREACHABLE = true;
    private static final boolean REACHABLE = false;
    private static final boolean ON_TIMEOUT = true;
    private static final int FIRST_ENTRY = 32287;
    private static final int RETREAT_LOCK = 120;
    private static final int CONTAIN_TIMEOUT = SquadManager.CONTAINMENT_TIMEOUT_FRAMES;
    private static final int CANNON_SUPPLY = 6;

    private static int supply(UnitType type, int count) {
        return type.supplyRequired() * count;
    }

    private static int cyclesTimeout(int index) {
        return FIRST_ENTRY + index * (CONTAIN_TIMEOUT + RETREAT_LOCK) + CONTAIN_TIMEOUT;
    }

    private static ContainmentVerdict timeout(ContainmentEscalation escalation, ContainmentStalemate stalemate,
                                              boolean staticOnly, boolean unreachable, int frame) {
        int reentries = escalation.getReentries();
        BooleanSupplier read = () -> staticOnly;
        ContainmentVerdict escalated = escalatedVerdict(ContainmentVerdict.RETREAT, ON_TIMEOUT, escalation, read,
                frame);
        return stalemateVerdict(escalated, ON_TIMEOUT, reentries, staticOnly, unreachable, stalemate, frame);
    }

    @Test
    void aTimeoutAgainstAnArmyIsAStalemateOnceTheRunReachesTheLimitOrTheBreakIsOutOfReach() {
        assertTrue(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, REACHABLE));
        assertFalse(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES - 1, ARMY_OUTSIDE, REACHABLE));
        assertTrue(ContainmentStalemate.isStalemate(0, ARMY_OUTSIDE, UNREACHABLE));
        assertFalse(ContainmentStalemate.isStalemate(STALEMATE_AFTER_REENTRIES, STATIC_ONLY, UNREACHABLE));
    }

    @Test
    void theArmyBackedLoopLeavesTheArcOnTheThirdTimeoutInsteadOfReenteringIt() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        for (int i = 0; i < STALEMATE_AFTER_REENTRIES; i++) {
            int frame = cyclesTimeout(i);
            assertEquals(ContainmentVerdict.RETREAT, timeout(escalation, stalemate, ARMY_OUTSIDE, REACHABLE, frame));
            assertFalse(stalemate.holdsEntry(frame + RETREAT_LOCK));
            escalation.onEntered(frame + RETREAT_LOCK);
        }
        int stalemateFrame = cyclesTimeout(STALEMATE_AFTER_REENTRIES);
        assertEquals(ContainmentVerdict.STALEMATE,
                timeout(escalation, stalemate, ARMY_OUTSIDE, REACHABLE, stalemateFrame));
        assertTrue(stalemate.isDetected());
        assertEquals(1, stalemate.getDetections());
        assertEquals(stalemateFrame, stalemate.getLastDetectedFrame());
        assertTrue(stalemate.holdsEntry(stalemateFrame + RETREAT_LOCK));
        assertTrue(stalemate.holdsEntry(stalemateFrame + HOLD_FRAMES - 1));
        assertFalse(stalemate.holdsEntry(stalemateFrame + HOLD_FRAMES));
    }

    @Test
    void anUnreachableBreakLeavesTheArcOnTheFirstTimeout() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        assertEquals(ContainmentVerdict.STALEMATE,
                timeout(escalation, stalemate, ARMY_OUTSIDE, UNREACHABLE, cyclesTimeout(0)));
    }

    @Test
    void aStaticOnlyDefenceStillEscalatesAndIsNeverAStalemate() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        escalation.onEntered(FIRST_ENTRY);
        for (int i = 0; i < ContainmentEscalation.ESCALATE_AFTER_REENTRIES; i++) {
            assertEquals(ContainmentVerdict.RETREAT,
                    timeout(escalation, stalemate, STATIC_ONLY, UNREACHABLE, cyclesTimeout(i)));
            escalation.onEntered(cyclesTimeout(i) + RETREAT_LOCK);
        }
        int frame = cyclesTimeout(ContainmentEscalation.ESCALATE_AFTER_REENTRIES);
        assertEquals(ContainmentVerdict.ESCALATE, timeout(escalation, stalemate, STATIC_ONLY, UNREACHABLE, frame));
        assertFalse(stalemate.isDetected());
        assertFalse(stalemate.holdsEntry(frame));
    }

    @Test
    void aDetectedStalemateMakesTheNextTimeoutAStalemateAtOnce() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(stalemate.onTimedOut(STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, REACHABLE, FIRST_ENTRY));
        int nextTimeout = FIRST_ENTRY + HOLD_FRAMES + CONTAIN_TIMEOUT;
        assertTrue(stalemate.onTimedOut(0, ARMY_OUTSIDE, REACHABLE, nextTimeout));
        assertEquals(2, stalemate.getDetections());
        assertTrue(stalemate.holdsEntry(nextTimeout + HOLD_FRAMES - 1));
        assertFalse(stalemate.onTimedOut(0, STATIC_ONLY, REACHABLE, nextTimeout + 1));
    }

    @Test
    void anyOtherEndToAContainClearsTheStalemateButNotItsHold() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(stalemate.onTimedOut(0, ARMY_OUTSIDE, UNREACHABLE, FIRST_ENTRY));
        stalemate.onEndedOtherwise();
        assertFalse(stalemate.isDetected());
        assertTrue(stalemate.holdsEntry(FIRST_ENTRY + 1));
        assertFalse(stalemate.onTimedOut(0, ARMY_OUTSIDE, REACHABLE, FIRST_ENTRY + HOLD_FRAMES + CONTAIN_TIMEOUT));
    }

    @Test
    void theHoldOutlastsTheReentryWindowSoTheNextContainStartsANewRun() {
        assertTrue(HOLD_FRAMES > ContainmentEscalation.REENTRY_WINDOW_FRAMES);
        ContainmentEscalation escalation = new ContainmentEscalation();
        escalation.onEntered(FIRST_ENTRY);
        assertFalse(escalation.onTimedOut(ARMY_OUTSIDE, cyclesTimeout(0)));
        escalation.onEntered(cyclesTimeout(0) + HOLD_FRAMES);
        assertEquals(0, escalation.getReentries());
    }

    @Test
    void onlyATimeoutRetreatIsReadForAStalemate() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertEquals(ContainmentVerdict.ESCALATE, stalemateVerdict(ContainmentVerdict.ESCALATE, ON_TIMEOUT,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertEquals(ContainmentVerdict.RETREAT, stalemateVerdict(ContainmentVerdict.RETREAT, false,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertEquals(ContainmentVerdict.BREAK_ALL, stalemateVerdict(ContainmentVerdict.BREAK_ALL, ON_TIMEOUT,
                STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, UNREACHABLE, stalemate, FIRST_ENTRY));
        assertFalse(stalemate.isDetected());
        assertFalse(stalemate.holdsEntry(FIRST_ENTRY));
    }

    @Test
    void aStalemateExitIsBridgedByTheHeldTimerLikeATimeout() {
        assertFalse(SquadManager.breaksHeldContain(ContainmentVerdict.STALEMATE,
                SquadManager.containmentExitPath(false, false)));
    }

    @Test
    void theBreakShortfallIsTheSupplyTheBreakRatioStillNeeds() {
        int enemy = supply(UnitType.Protoss_Zealot, 34) + supply(UnitType.Protoss_Dragoon, 11)
                + supply(UnitType.Protoss_High_Templar, 5) + 2 * CANNON_SUPPLY;
        int ours = 308;
        ContainmentEvaluator.BreakMeasure measure = new ContainmentEvaluator.BreakMeasure(ours, enemy);
        assertEquals(212, enemy);
        assertEquals(10, measure.shortfall());
        assertFalse(measure.breaks());
        assertFalse(measure.unreachable());
        assertEquals(0, new ContainmentEvaluator.BreakMeasure(318, enemy).shortfall());
        assertTrue(new ContainmentEvaluator.BreakMeasure(318, enemy).breaks());
        assertEquals(0, new ContainmentEvaluator.BreakMeasure(500, enemy).shortfall());
    }

    @Test
    void aBreakAboveTheSupplyCapIsUnreachable() {
        int enemy = supply(UnitType.Protoss_Zealot, 40) + supply(UnitType.Protoss_Dragoon, 31)
                + supply(UnitType.Protoss_High_Templar, 5) + 2 * CANNON_SUPPLY;
        assertEquals(316, enemy);
        assertTrue(ContainmentEvaluator.breakUnreachable(enemy));
        assertFalse(ContainmentEvaluator.breakUnreachable(266));
        assertTrue(ContainmentEvaluator.breakUnreachable(267));
        assertFalse(new ContainmentEvaluator.BreakMeasure(ContainmentEvaluator.MAX_SUPPLY, enemy).breaks());
    }

    private static ContainmentStalemate detected() {
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(stalemate.onTimedOut(STALEMATE_AFTER_REENTRIES, ARMY_OUTSIDE, REACHABLE, FIRST_ENTRY));
        return stalemate;
    }

    @Test
    void theCommitStartsOnlyForADetectedStalemateAtTheSupplyThresholdWithAnArmyAndATarget() {
        assertTrue(ContainmentStalemate.commitStarts(true, COMMIT_SUPPLY_USED, 300, true));
        assertTrue(ContainmentStalemate.commitStarts(true, 400, 300, true));
        assertFalse(ContainmentStalemate.commitStarts(true, COMMIT_SUPPLY_USED - 1, 300, true));
        assertEquals(360, COMMIT_SUPPLY_USED);
        assertFalse(ContainmentStalemate.commitStarts(true, 359, 300, true));
        assertTrue(ContainmentStalemate.commitStarts(true, 370, 300, true));
        assertFalse(ContainmentStalemate.commitStarts(false, 400, 300, true));
        assertFalse(ContainmentStalemate.commitStarts(true, 400, 300, false));
        assertFalse(ContainmentStalemate.commitStarts(true, 400, 0, true));
    }

    @Test
    void theCommitReleasesBelowHalfItsCommittedSupplyOrWithNoTarget() {
        assertFalse(ContainmentStalemate.commitReleases(150, 300, true));
        assertTrue(ContainmentStalemate.commitReleases(149, 300, true));
        assertFalse(ContainmentStalemate.commitReleases(151, 301, true));
        assertTrue(ContainmentStalemate.commitReleases(150, 301, true));
        assertTrue(ContainmentStalemate.commitReleases(300, 300, false));
    }

    @Test
    void aStaleOrClearedStalemateNeverCommitsAMaxedArmy() {
        ContainmentStalemate never = new ContainmentStalemate();
        assertEquals(CommitChange.NONE, never.onFrame(400, 300, true));
        ContainmentStalemate cleared = detected();
        cleared.onEndedOtherwise();
        assertEquals(CommitChange.NONE, cleared.onFrame(400, 300, true));
        assertFalse(cleared.isCommitting());
    }

    @Test
    void aMaxedArmyCommitsThenReleasesAtTheFloorAndCommitsAgainOnceRemaxed() {
        ContainmentStalemate stalemate = detected();
        assertEquals(CommitChange.NONE, stalemate.onFrame(COMMIT_SUPPLY_USED - 1, 300, true));
        assertEquals(CommitChange.STARTED, stalemate.onFrame(COMMIT_SUPPLY_USED, 300, true));
        assertTrue(stalemate.isCommitting());
        assertEquals(300, stalemate.getCommittedSupply());
        assertEquals(CommitChange.NONE, stalemate.onFrame(250, 150, true));
        assertEquals(CommitChange.RELEASED, stalemate.onFrame(240, 149, true));
        assertFalse(stalemate.isCommitting());
        assertEquals(CommitChange.NONE, stalemate.onFrame(350, 280, true));
        assertEquals(CommitChange.STARTED, stalemate.onFrame(370, 290, true));
        assertEquals(290, stalemate.getCommittedSupply());
        assertEquals(2, stalemate.getCommits());
    }

    @Test
    void theCommitReleasesWhenNoTargetIsKnown() {
        ContainmentStalemate stalemate = detected();
        assertEquals(CommitChange.STARTED, stalemate.onFrame(400, 300, true));
        assertEquals(CommitChange.RELEASED, stalemate.onFrame(400, 300, false));
        assertEquals(CommitChange.NONE, stalemate.onFrame(400, 300, false));
    }

    @Test
    void aStormRetreatHoldsACommittedSquadOnlyWhileItsRetreatLockLasts() {
        assertTrue(ContainmentStalemate.stormRetreatHolds(SquadStatus.RETREAT, true));
        assertFalse(ContainmentStalemate.stormRetreatHolds(SquadStatus.RETREAT, false));
        assertFalse(ContainmentStalemate.stormRetreatHolds(SquadStatus.FIGHT, true));
    }

    @Test
    void containmentEntryIsRefusedWhileTheStalemateHoldsEntry() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        int frame = FIRST_ENTRY;
        assertTrue(SquadManager.mayTakeArc(escalation, stalemate, frame, false, true, false));
        assertTrue(stalemate.onTimedOut(0, ARMY_OUTSIDE, UNREACHABLE, frame));
        assertTrue(stalemate.holdsEntry(frame + 1));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, frame + 1, false, true, false));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, frame + HOLD_FRAMES - 1, false, true, false));
        assertTrue(SquadManager.mayTakeArc(escalation, stalemate, frame + HOLD_FRAMES, false, true, false));
    }

    @Test
    void containmentEntryIsRefusedWhileTheArmyIsCommittedEvenAfterTheHold() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = detected();
        assertEquals(CommitChange.STARTED, stalemate.onFrame(400, 300, true));
        int afterHold = FIRST_ENTRY + HOLD_FRAMES;
        assertFalse(stalemate.holdsEntry(afterHold));
        assertTrue(stalemate.barsEntry(afterHold));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, afterHold, false, true, false));
        stalemate.onFrame(200, 100, true);
        assertTrue(SquadManager.mayTakeArc(escalation, stalemate, afterHold, false, true, false));
    }

    @Test
    void theEntrySeamKeepsTheOtherEntryRules() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, FIRST_ENTRY, true, true, false));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, FIRST_ENTRY, false, false, false));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, FIRST_ENTRY, false, true, true));
    }

    @Test
    void theGroundArmySupplyCountsOnlyGroundSquads() {
        assertEquals(0, SquadManager.groundArmySupply(java.util.Arrays.asList(new GroundSquad(), new AirSquad())));
    }

    @Test
    void theMeasuredBreakKeepsTheBreakRule() {
        assertEquals(ContainmentEvaluator.breaks(150, 100), new ContainmentEvaluator.BreakMeasure(150, 100).breaks());
        assertEquals(ContainmentEvaluator.breaks(149, 100), new ContainmentEvaluator.BreakMeasure(149, 100).breaks());
        assertTrue(ContainmentEvaluator.breaks(150, 100));
        assertFalse(ContainmentEvaluator.breaks(149, 100));
    }
}
