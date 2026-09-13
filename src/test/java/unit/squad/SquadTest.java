package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadTest {

    private static Squad squad(SquadStatus status) {
        Squad squad = new Squad();
        squad.setStatus(status);
        return squad;
    }

    private static Squad containingSquad(int containStartFrame) {
        Squad squad = squad(SquadStatus.CONTAIN);
        squad.setContainStartFrame(containStartFrame);
        return squad;
    }

    private static Squad merge(Squad... sources) {
        Squad merged = new Squad();
        merged.setStatus(SquadStatus.FIGHT);
        merged.inheritStateFrom(Arrays.asList(sources));
        return merged;
    }

    @Test
    void mergedStatusIsIndependentOfIterationOrder() {
        for (SquadStatus first : SquadStatus.values()) {
            for (SquadStatus second : SquadStatus.values()) {
                assertEquals(
                        merge(squad(first), squad(second)).getStatus(),
                        merge(squad(second), squad(first)).getStatus(),
                        first + " merged with " + second + " must not depend on order");
            }
        }
    }

    @Test
    void fightOutranksEveryOtherStatus() {
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.FIGHT), squad(SquadStatus.RETREAT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.FIGHT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.CONTAIN), squad(SquadStatus.FIGHT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.RALLY), squad(SquadStatus.FIGHT)).getStatus());
    }

    @Test
    void containOutranksRetreatAndRally() {
        assertEquals(SquadStatus.CONTAIN, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.CONTAIN)).getStatus());
        assertEquals(SquadStatus.CONTAIN, merge(squad(SquadStatus.RALLY), squad(SquadStatus.CONTAIN)).getStatus());
    }

    @Test
    void retreatOutranksRally() {
        assertEquals(SquadStatus.RETREAT, merge(squad(SquadStatus.RALLY), squad(SquadStatus.RETREAT)).getStatus());
    }

    @Test
    void mergedStatusIgnoresTheStatusOfTheSquadBeingMergedInto() {
        assertEquals(SquadStatus.RETREAT, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.RALLY)).getStatus());
    }

    @Test
    void containStartFrameCarriesEarliestStartWhenMergedStatusIsContain() {
        Squad merged = merge(containingSquad(600), containingSquad(200));
        Squad reversed = merge(containingSquad(200), containingSquad(600));

        assertEquals(SquadStatus.CONTAIN, merged.getStatus());
        assertEquals(200, merged.getContainStartFrame());
        assertEquals(merged.getContainStartFrame(), reversed.getContainStartFrame());
    }

    @Test
    void containStartFrameIsClearedWhenMergedStatusIsNotContain() {
        Squad merged = merge(containingSquad(200), squad(SquadStatus.FIGHT));

        assertEquals(SquadStatus.FIGHT, merged.getStatus());
        assertEquals(0, merged.getContainStartFrame());
    }

    @Test
    void hysteresisLocksTakeTheLaterDeadline() {
        Squad early = squad(SquadStatus.FIGHT);
        early.setFightLockedUntilFrame(100);
        early.setRetreatLockedUntilFrame(400);
        early.setContainLockedUntilFrame(150);

        Squad late = squad(SquadStatus.FIGHT);
        late.setFightLockedUntilFrame(300);
        late.setRetreatLockedUntilFrame(200);
        late.setContainLockedUntilFrame(500);

        Squad merged = merge(early, late);
        Squad reversed = merge(late, early);

        assertEquals(300, merged.getFightLockedUntilFrame());
        assertEquals(400, merged.getRetreatLockedUntilFrame());
        assertEquals(500, merged.getContainLockedUntilFrame());
        assertEquals(merged.getFightLockedUntilFrame(), reversed.getFightLockedUntilFrame());
        assertEquals(merged.getRetreatLockedUntilFrame(), reversed.getRetreatLockedUntilFrame());
        assertEquals(merged.getContainLockedUntilFrame(), reversed.getContainLockedUntilFrame());
    }

    @Test
    void advanceDoesNotArmOrExtendFightLock() {
        Squad squad = squad(SquadStatus.FIGHT);
        squad.setFightLockedUntilFrame(100);

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ADVANCE, false, 200);

        assertEquals(100, squad.getFightLockedUntilFrame());
    }

    private static Squad lockedFightSquad(int supply, int armedFrame) {
        Squad squad = squad(SquadStatus.FIGHT);
        squad.setCachedSupply(supply);
        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, armedFrame);
        return squad;
    }

    private static int window(Squad squad) {
        return squad.getFightHysteresis().getFrames();
    }

    @Test
    void engageRenewsTheLockOfASquadAtFullStrength() {
        Squad squad = lockedFightSquad(12, 4582);
        int expiry = squad.getFightLockedUntilFrame();

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, expiry);

        assertEquals(expiry + window(squad), squad.getFightLockedUntilFrame());
        assertTrue(squad.isFightLocked(expiry + 2));
    }

    /**
     * L9NW30UL: the lock armed at frame 4582 with six Zerglings was renewed at its expiry on frame 4654
     * after one had died, and so held the frame 4656 RETREAT.
     */
    @Test
    void engageDoesNotRenewTheLockOfASquadThatLostSupply() {
        Squad squad = lockedFightSquad(12, 4582);
        int expiry = squad.getFightLockedUntilFrame();
        squad.setCachedSupply(10);

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, expiry);

        assertEquals(expiry, squad.getFightLockedUntilFrame());
        assertFalse(squad.isFightLocked(expiry + 2));
        assertFalse(SquadManager.fightLockHolds(squad.isFightLocked(expiry + 2),
                CombatSimulator.CombatResult.RETREAT, true, 1.3312, 1.4));
    }

    @Test
    void lockDoesNotExtendWhileTheSquadKeepsLosingUnits() {
        Squad squad = lockedFightSquad(12, 1000);
        int expiry = squad.getFightLockedUntilFrame();

        int supply = 12;
        for (int frame = 1001; frame < expiry + window(squad); frame++) {
            if (frame % 20 == 0 && supply > 2) {
                supply -= 2;
                squad.setCachedSupply(supply);
            }
            if (!squad.isFightLocked(frame)) {
                SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, frame);
            }
        }

        assertEquals(expiry, squad.getFightLockedUntilFrame());
    }

    @Test
    void reinforcementRaisesTheCommittedSupplySoLaterLossesStillBlockRenewal() {
        Squad squad = lockedFightSquad(12, 1000);
        int expiry = squad.getFightLockedUntilFrame();
        squad.setCachedSupply(16);
        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, expiry);
        int renewedExpiry = squad.getFightLockedUntilFrame();
        squad.setCachedSupply(14);

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, renewedExpiry);

        assertEquals(renewedExpiry, squad.getFightLockedUntilFrame());
    }

    @Test
    void freshLockIsAllowedAfterAFullWindowWithoutOne() {
        Squad squad = lockedFightSquad(12, 1000);
        int expiry = squad.getFightLockedUntilFrame();
        squad.setCachedSupply(6);
        int fresh = expiry + window(squad);

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, false, fresh);

        assertEquals(fresh + window(squad), squad.getFightLockedUntilFrame());
        assertEquals(6, squad.getFightLockSupply());
    }

    @Test
    void retreatLockStillBlocksTheFightLock() {
        Squad squad = squad(SquadStatus.FIGHT);
        squad.setCachedSupply(12);

        SquadManager.updateFightLock(squad, CombatSimulator.CombatResult.ENGAGE, true, 1000);

        assertEquals(0, squad.getFightLockedUntilFrame());
    }
}
