package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class LurkerHoldTest {

    @Test
    void aLurkerHitInsideATanksReachIsSentOut() {
        assertEquals(LurkerHold.HIT, LurkerHold.reason(false, true, false, false));
    }

    @Test
    void aLurkerRetreatingInsideFixedFireIsSentOut() {
        assertEquals(LurkerHold.RETREAT, LurkerHold.reason(false, false, true, false));
    }

    @Test
    void aLurkerOutOfFireIsLeftAlone() {
        assertNull(LurkerHold.reason(false, false, false, false));
    }

    @Test
    void aHoldingLurkerKeepsItsPointWhileTheFireStaysOffIt() {
        assertNull(LurkerHold.reason(false, true, true, true));
    }

    @Test
    void aHoldingLurkerMovesWhenTheFireMovesOntoItsPoint() {
        assertEquals(LurkerHold.MOVED, LurkerHold.reason(true, false, false, true));
    }

    @Test
    void aSquadFightingOnAnEngageVerdictIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, false, ENGAGE));
    }

    @Test
    void aSquadUnderItsFightLockIsCommitting() {
        assertTrue(SquadManager.isCommitting(SquadStatus.FIGHT, true, ADVANCE));
    }

    @Test
    void aBlindAdvanceIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, ADVANCE));
        assertFalse(SquadManager.isCommitting(SquadStatus.FIGHT, false, null));
    }

    @Test
    void aSquadNotFightingIsNotCommitting() {
        assertFalse(SquadManager.isCommitting(SquadStatus.RETREAT, false, ENGAGE));
        assertFalse(SquadManager.isCommitting(SquadStatus.CONTAIN, true, RETREAT));
    }

    @Test
    void aSquadNotCommittingHasNoCommitmentRun() {
        assertNull(LurkerHold.committingSince(false, 100, 200));
        assertNull(LurkerHold.committingSince(false, null, 200));
    }

    @Test
    void aCommitmentRunStartsOnItsFirstFrameAndKeepsItsStart() {
        assertEquals(Integer.valueOf(200), LurkerHold.committingSince(true, null, 200));
        assertEquals(Integer.valueOf(150), LurkerHold.committingSince(true, 150, 200));
    }

    @Test
    void aSingleEngageDoesNotCommitTheLurkers() {
        assertFalse(LurkerHold.lurkersCommit(200, 200));
        assertFalse(LurkerHold.lurkersCommit(200, 206));
        assertFalse(LurkerHold.lurkersCommit(null, 200));
    }

    @Test
    void theLurkersCommitOnceTheSquadHasCommittedForTheWholeWindow() {
        assertFalse(LurkerHold.lurkersCommit(200, 200 + LurkerHold.COMMIT_FRAMES - 1));
        assertTrue(LurkerHold.lurkersCommit(200, 200 + LurkerHold.COMMIT_FRAMES));
    }

    @Test
    void anEngageBrokenByARetreatStartsTheWindowAgain() {
        Integer since = LurkerHold.committingSince(true, null, 100);
        since = LurkerHold.committingSince(true, since, 150);
        since = LurkerHold.committingSince(false, since, 160);
        since = LurkerHold.committingSince(true, since, 170);

        assertFalse(LurkerHold.lurkersCommit(since, 100 + LurkerHold.COMMIT_FRAMES));
        assertTrue(LurkerHold.lurkersCommit(since, 170 + LurkerHold.COMMIT_FRAMES));
    }

    @Test
    void aHoldIsMovedOnlyForAPointThatGainsGround() {
        assertFalse(LurkerHold.worthMoving(-40, -40));
        assertFalse(LurkerHold.worthMoving(-40, -40 + LurkerHold.MOVE_GAIN - 1));
        assertTrue(LurkerHold.worthMoving(-40, -40 + LurkerHold.MOVE_GAIN));
    }

    @Test
    void aSingleEngageAndTheFightLockItArmsNeverCommitTheLurkers() {
        int engage = 1000;
        int lockEnd = engage + 72;
        Integer since = null;
        for (int frame = engage; frame <= lockEnd + 30; frame++) {
            boolean committing = SquadManager.isCommitting(SquadStatus.FIGHT, frame < lockEnd,
                    frame == engage ? ENGAGE : ADVANCE);
            since = LurkerHold.committingSince(committing, since, frame);
            assertFalse(LurkerHold.lurkersCommit(since, frame));
        }
    }

    @Test
    void anEngageHeldAcrossTheWindowCommitsTheLurkers() {
        Integer since = null;
        int frame = 1000;
        for (; frame < 1000 + LurkerHold.COMMIT_FRAMES; frame++) {
            since = LurkerHold.committingSince(SquadManager.isCommitting(SquadStatus.FIGHT, true, ENGAGE), since,
                    frame);
            assertFalse(LurkerHold.lurkersCommit(since, frame));
        }
        since = LurkerHold.committingSince(SquadManager.isCommitting(SquadStatus.FIGHT, true, ENGAGE), since, frame);
        assertTrue(LurkerHold.lurkersCommit(since, frame));
    }

    @Test
    void aContainBreakCommitsTheLurkersAtOnceWhileTheSquadKeepsCommitting() {
        int broken = 5000;
        Integer since = LurkerHold.alreadyCommittedSince(broken);

        assertTrue(LurkerHold.lurkersCommit(since, broken));
        since = LurkerHold.committingSince(SquadManager.isCommitting(SquadStatus.FIGHT, true, null), since,
                broken + 1);
        assertTrue(LurkerHold.lurkersCommit(since, broken + 1));
        since = LurkerHold.committingSince(SquadManager.isCommitting(SquadStatus.RETREAT, false, null), since,
                broken + 2);
        assertFalse(LurkerHold.lurkersCommit(since, broken + 2));
    }
}
