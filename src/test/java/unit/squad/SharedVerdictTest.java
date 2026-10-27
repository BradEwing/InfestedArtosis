package unit.squad;

import config.Config;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedVerdictTest {

    private static final int NOW = 5300;
    private static final BiPredicate<Squad, Squad> ALWAYS_NEAR = (first, second) -> true;
    private static final BiPredicate<Squad, Squad> NEVER_NEAR = (first, second) -> false;

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

    private static Squad squad(SquadStatus status) {
        return squad(status, 0);
    }

    private static Squad squad(SquadStatus status, int supply) {
        Squad squad = new SizedSquad(supply);
        squad.setStatus(status);
        return squad;
    }

    private static List<SharedVerdict.Turn> reconcile(List<Squad> squads, BiPredicate<Squad, Squad> near) {
        return SharedVerdict.reconcile(squads, Collections.emptySet(), near, NOW, squad -> { }, squad -> { });
    }

    @Test
    void twoNeighboursNeverHoldOppositeStatusesAfterTheDecisionStep() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);
        List<Squad> squads = Arrays.asList(fighter, retreater);

        reconcile(squads, ALWAYS_NEAR);

        assertEquals(0, SharedVerdict.countOpposed(squads, ALWAYS_NEAR));
        assertEquals(fighter.getStatus(), retreater.getStatus());
    }

    @Test
    void anyMixOfLocksLeavesNoOpposedPair() {
        for (int mask = 0; mask < 4; mask++) {
            Squad fighter = squad(SquadStatus.FIGHT, 6);
            Squad retreater = squad(SquadStatus.RETREAT, 4);
            if ((mask & 1) != 0) {
                fighter.startFightLock(NOW);
            }
            if ((mask & 2) != 0) {
                retreater.startRetreatLock(NOW);
            }
            List<Squad> squads = Arrays.asList(fighter, retreater);

            reconcile(squads, ALWAYS_NEAR);

            assertEquals(0, SharedVerdict.countOpposed(squads, ALWAYS_NEAR));
        }
    }

    @Test
    void aChainOfSquadsSettlesOnOneStatusAndStaysThereOnTheNextFrame() {
        Squad a = squad(SquadStatus.FIGHT, 10);
        a.startFightLock(NOW);
        Squad b = squad(SquadStatus.RETREAT, 5);
        Squad c = squad(SquadStatus.RETREAT, 20);
        c.startRetreatLock(NOW);
        List<Squad> squads = Arrays.asList(a, b, c);
        BiPredicate<Squad, Squad> chain = (first, second) -> first != second && (first == b || second == b);

        List<SharedVerdict.Turn> first = reconcile(squads, chain);
        List<SharedVerdict.Turn> second = reconcile(squads, chain);

        assertEquals(0, SharedVerdict.countOpposed(squads, chain));
        assertTrue(second.isEmpty());
        assertEquals(first.size(), first.stream().map(turn -> turn.squad).distinct().count());
        assertEquals(a.getStatus(), b.getStatus());
        assertEquals(b.getStatus(), c.getStatus());
    }

    @Test
    void aFrozenSquadPullsItsNeighbourToItsOwnStatus() {
        Squad frozenFighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT, 30);
        retreater.startRetreatLock(NOW);

        SharedVerdict.reconcile(Arrays.asList(frozenFighter, retreater), Collections.singleton(frozenFighter),
                ALWAYS_NEAR, NOW, squad -> { }, squad -> { });

        assertEquals(SquadStatus.FIGHT, frozenFighter.getStatus());
        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
    }

    @Test
    void frozenSquadsThatDisagreeLeaveTheGroupAlone() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);

        List<SharedVerdict.Turn> turns = SharedVerdict.reconcile(Arrays.asList(fighter, retreater),
                new HashSet<>(Arrays.asList(fighter, retreater)), ALWAYS_NEAR, NOW, squad -> { }, squad -> { });

        assertTrue(turns.isEmpty());
    }

    @Test
    void aTurnedSquadAdoptsTheWinningSidesLockAndDropsTheOther() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        fighter.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        retreater.startRetreatLock(NOW);

        List<SharedVerdict.Turn> turns = reconcile(Arrays.asList(fighter, retreater), ALWAYS_NEAR);

        assertEquals(1, turns.size());
        assertEquals(SquadStatus.RETREAT, turns.get(0).from);
        assertEquals(SquadStatus.FIGHT, turns.get(0).to);
        assertEquals(fighter.getFightLockedUntilFrame(), retreater.getFightLockedUntilFrame());
        assertFalse(retreater.isRetreatLocked(NOW));
    }

    @Test
    void aSquadTurnedToRetreatLosesItsFightLock() {
        Squad fighter = squad(SquadStatus.FIGHT, 2);
        fighter.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 20);
        retreater.startRetreatLock(NOW);

        reconcile(Arrays.asList(fighter, retreater), ALWAYS_NEAR);

        assertEquals(SquadStatus.RETREAT, fighter.getStatus());
        assertFalse(fighter.isFightLocked(NOW));
        assertTrue(fighter.isRetreatLocked(NOW));
    }

    @Test
    void aLockDoublesASidesSupplyButDoesNotBeatAMuchLargerOne() {
        assertFalse(SharedVerdict.fightWins(10, false, 10, false));
        assertTrue(SharedVerdict.fightWins(11, false, 10, false));
        assertTrue(SharedVerdict.fightWins(6, true, 10, false));
        assertFalse(SharedVerdict.fightWins(40, false, 21, true));
        assertTrue(SharedVerdict.fightWins(40, false, 4, true));
        assertFalse(SharedVerdict.fightWins(10, true, 10, true));
    }

    @Test
    void aTurnBackCommitCountsAsAHoldOnTheFightSide() {
        Squad fighter = squad(SquadStatus.FIGHT, 6);
        fighter.startTurnBackCommit(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        reconcile(Arrays.asList(fighter, retreater), ALWAYS_NEAR);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
    }

    @Test
    void squadsOutsideTheRadiusKeepTheirOwnStatus() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);

        List<SharedVerdict.Turn> turned = reconcile(Arrays.asList(fighter, retreater), NEVER_NEAR);

        assertTrue(turned.isEmpty());
        assertEquals(SquadStatus.FIGHT, fighter.getStatus());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
    }

    @Test
    void airSquadsAndNonDecisionStatusesAreNotPaired() {
        Squad air = new AirSquad();
        air.setStatus(SquadStatus.RETREAT);
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad rallying = squad(SquadStatus.RALLY);

        assertEquals(0, SharedVerdict.countOpposed(Arrays.asList(air, fighter, rallying), ALWAYS_NEAR));
    }

    @Test
    void aNeighbourFightLockHoldsAFightSquad() {
        Squad locked = squad(SquadStatus.FIGHT);
        locked.startFightLock(NOW);
        Squad neighbour = squad(SquadStatus.FIGHT);
        List<Squad> squads = Arrays.asList(locked, neighbour);

        assertTrue(SharedVerdict.neighbourFightLock(neighbour, squads, ALWAYS_NEAR, NOW + 1));
        assertFalse(SharedVerdict.neighbourFightLock(neighbour, squads, NEVER_NEAR, NOW + 1));
        assertFalse(SharedVerdict.neighbourFightLock(neighbour, squads, ALWAYS_NEAR, NOW + 10_000));
        assertFalse(SharedVerdict.neighbourFightLock(locked, Collections.singletonList(locked), ALWAYS_NEAR, NOW + 1));
    }

    @Test
    void aNeighbourFightLockDoesNotHoldARetreatingSquad() {
        Squad locked = squad(SquadStatus.FIGHT);
        locked.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT);

        assertFalse(SharedVerdict.neighbourFightLock(retreater, Arrays.asList(locked, retreater), ALWAYS_NEAR,
                NOW + 1));
    }

    @Test
    void aSquadHeldAtItsStandoffPointIsNotCountedAndTheRetreatingNeighbourIsNotTurnedToFight() {
        Squad held = squad(SquadStatus.FIGHT, 24);
        Squad retreater = squad(SquadStatus.RETREAT, 8);
        retreater.startRetreatLock(NOW);
        List<Squad> squads = Arrays.asList(held, retreater);

        List<Squad> considered = SharedVerdict.withoutHeld(squads, squad -> squad == held);
        List<SharedVerdict.Turn> turns = reconcile(considered, ALWAYS_NEAR);

        assertEquals(Collections.singletonList(retreater), considered);
        assertTrue(turns.isEmpty());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
        assertEquals(SquadStatus.FIGHT, held.getStatus());
    }

    @Test
    void withoutTheHeldFilterTheLargerStagedSquadWouldTurnTheNeighbour() {
        Squad held = squad(SquadStatus.FIGHT, 24);
        Squad retreater = squad(SquadStatus.RETREAT, 8);
        retreater.startRetreatLock(NOW);

        reconcile(Arrays.asList(held, retreater), ALWAYS_NEAR);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
    }

    @Test
    void anEscapeBlockedFightSquadPullsItsGroupToFightAndIsNotTurned() {
        Squad blocked = squad(SquadStatus.FIGHT, 12);
        Squad retreater = squad(SquadStatus.RETREAT, 16);
        retreater.startRetreatLock(NOW);
        List<Squad> squads = Arrays.asList(blocked, retreater);

        assertTrue(SharedVerdict.escapeBlockedFight(true, blocked.getStatus(), 1));
        List<SharedVerdict.Turn> turns = SharedVerdict.reconcile(squads, Collections.singleton(blocked),
                ALWAYS_NEAR, NOW, squad -> { }, squad -> { });

        assertEquals(SquadStatus.FIGHT, blocked.getStatus());
        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertEquals(1, turns.size());
    }

    @Test
    void theEscapeFreezeNeedsTheSwitchAFightStatusAndABlockedReading() {
        assertFalse(SharedVerdict.escapeBlockedFight(false, SquadStatus.FIGHT, 1));
        assertFalse(SharedVerdict.escapeBlockedFight(true, SquadStatus.RETREAT, 1));
        assertFalse(SharedVerdict.escapeBlockedFight(true, SquadStatus.FIGHT, 0));
        assertFalse(SharedVerdict.escapeBlockedFight(true, SquadStatus.FIGHT, -1));
    }

    @Test
    void leavingTheStandoffClosesAnOpenEpisodeOnlyWithStagingOn() {
        Squad staged = squad(SquadStatus.FIGHT, 10);
        staged.getStandoff().begin(NOW, null);

        SquadManager.leaveStandoff(false, staged, "ENGAGED", NOW + 1);
        assertTrue(staged.getStandoff().isActive());

        SquadManager.leaveStandoff(true, staged, "ENGAGED", NOW + 1);
        assertFalse(staged.getStandoff().isActive());
        assertTrue(staged.getStandoff().isReleased(NOW + 2));
    }

    @Test
    void theSwitchGatesTheNeighbourLockInTheProductionHelper() {
        boolean saved = Config.squadSharedVerdict;
        try {
            Squad locked = squad(SquadStatus.FIGHT);
            locked.startFightLock(NOW);
            Squad neighbour = squad(SquadStatus.FIGHT);
            List<Squad> squads = Arrays.asList(locked, neighbour);

            Config.squadSharedVerdict = false;
            assertFalse(SquadManager.neighbourLockApplies(neighbour, squads, ALWAYS_NEAR, NOW + 1));
            assertFalse(SquadManager.fightLockHolds(neighbour.isFightLocked(NOW + 1),
                    CombatSimulator.CombatResult.RETREAT, true, 1.0, 1.3));

            Config.squadSharedVerdict = true;
            assertTrue(SquadManager.neighbourLockApplies(neighbour, squads, ALWAYS_NEAR, NOW + 1));
        } finally {
            Config.squadSharedVerdict = saved;
        }
    }
}
