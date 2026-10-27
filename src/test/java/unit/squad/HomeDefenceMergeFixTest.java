package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;
import telemetry.RetreatRoute;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class HomeDefenceMergeFixTest {

    private static final int NOW = 9000;
    private static final double RETREAT_THRESHOLD = 0.8;
    private static final BiPredicate<Squad, Squad> ALWAYS_NEAR = (first, second) -> true;

    private static class SizedSquad extends GroundSquad {
        private final int supply;

        SizedSquad(int supply) {
            this.supply = supply;
        }

        @Override
        public int getSupply() {
            return supply;
        }
    }

    private static Squad squad(SquadStatus status, int supply) {
        Squad squad = new SizedSquad(supply);
        squad.setStatus(status);
        return squad;
    }

    @Test
    void aSquadFarFromEveryBaseIsNotAHomeDefenderWhateverThreatensHome() {
        assertFalse(SquadManager.defendsHome(true, true, SquadStatus.FIGHT, false));
        assertTrue(SquadManager.defendsHome(true, true, SquadStatus.FIGHT, true));
    }

    @Test
    void aPinnedHomeDefenderIsNeitherTurnedNorDragsAHeavierLockedRetreater() {
        Squad defender = squad(SquadStatus.FIGHT, 6);
        Squad retreater = squad(SquadStatus.RETREAT, 24);
        retreater.startRetreatLock(NOW);

        List<SharedVerdict.Turn> turns = SharedVerdict.reconcile(Arrays.asList(defender, retreater),
                Collections.emptySet(), Collections.singleton(defender), ALWAYS_NEAR, NOW, s -> { }, s -> { });

        assertTrue(turns.isEmpty());
        assertEquals(SquadStatus.FIGHT, defender.getStatus());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
    }

    @Test
    void anEscapeBlockedSquadStaysPinnedAndIsNotFrozenDuringAHomeThreat() {
        Squad blocked = squad(SquadStatus.FIGHT, 6);
        Squad retreater = squad(SquadStatus.RETREAT, 24);
        retreater.startRetreatLock(NOW);

        SharedVerdict.reconcile(Arrays.asList(blocked, retreater), Collections.emptySet(),
                Collections.singleton(blocked), ALWAYS_NEAR, NOW, s -> { }, s -> { });

        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
        assertEquals(SquadStatus.FIGHT, blocked.getStatus());
    }

    @Test
    void aDefendTurnOpensTheTurnBackCommitOnlyAtOrAboveTheFloorWithBothSwitchesOn() {
        DecisionPath defend = DecisionPath.HOME_CONTESTED_DEFEND;

        assertTrue(SquadManager.startsDefendCommit(true, true, defend, 1.0, RETREAT_THRESHOLD));
        assertFalse(SquadManager.startsDefendCommit(false, true, defend, 1.0, RETREAT_THRESHOLD));
        assertFalse(SquadManager.startsDefendCommit(true, false, defend, 1.0, RETREAT_THRESHOLD));
        assertFalse(SquadManager.startsDefendCommit(true, true, defend, 0.5, RETREAT_THRESHOLD));
        assertFalse(SquadManager.startsDefendCommit(true, true, DecisionPath.CORNERED_ENGAGE, 1.0,
                RETREAT_THRESHOLD));
    }

    @Test
    void afterADefendTurnARatioInsideTheBandKeepsFightForTheCommitWindowAndBelowTheThresholdLeaves() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.setRetreatRoute(RetreatRoute.HOME_CONTESTED);
        squad.startRetreatLock(NOW - 10);

        SquadManager.turnCorneredSquadToFight(squad, RETREAT, NOW);
        squad.startTurnBackCommit(NOW);
        int afterHold = NOW + squad.getFightHysteresis().getFrames() + 1;

        assertFalse(squad.isCorneredFightHeld(afterHold));
        boolean commitHolds = SquadManager.turnBackCommitHolds(squad.isTurnBackCommitted(afterHold), RETREAT, true,
                1.0, RETREAT_THRESHOLD);
        assertTrue(commitHolds);
        assertTrue(SquadManager.fightHeld(squad, afterHold, commitHolds));
        assertFalse(SquadManager.turnBackCommitHolds(squad.isTurnBackCommitted(afterHold), RETREAT, true,
                RETREAT_THRESHOLD - 0.1, RETREAT_THRESHOLD));
    }

    @Test
    void anEscapeBlockedFailedStagingFightRenewsItsHoldInsteadOfDroppingThroughRetreat() {
        assertTrue(SquadManager.rearmsEscapeBlockedHold(true, true, SquadStatus.FIGHT, false, true, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(false, true, SquadStatus.FIGHT, false, true, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(true, false, SquadStatus.FIGHT, false, true, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(true, true, SquadStatus.FIGHT, true, true, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(true, true, SquadStatus.RETREAT, false, true, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(true, true, SquadStatus.FIGHT, false, false, true));
        assertFalse(SquadManager.rearmsEscapeBlockedHold(true, true, SquadStatus.FIGHT, false, true, false));
    }

    @Test
    void aSquadTurnedByAPinnedSquadWithNoLockGetsAFreshFightLock() {
        Squad pinned = squad(SquadStatus.FIGHT, 12);
        Squad retreater = squad(SquadStatus.RETREAT, 8);
        retreater.startRetreatLock(NOW);

        List<SharedVerdict.Turn> turns = SharedVerdict.reconcile(Arrays.asList(pinned, retreater),
                Collections.emptySet(), Collections.singleton(pinned), ALWAYS_NEAR, NOW, s -> { }, s -> { });

        assertEquals(1, turns.size());
        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertTrue(retreater.isFightLocked(NOW + 1));
        assertFalse(retreater.isRetreatLocked(NOW + 1));
    }

    @Test
    void aSquadTurnedWithoutAHoldWithNoLockStaysUnlocked() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        SharedVerdict.reconcile(Arrays.asList(fighter, retreater), new HashSet<>(), ALWAYS_NEAR, NOW, s -> { },
                s -> { });

        assertFalse(retreater.isFightLocked(NOW + 1));
    }
}
