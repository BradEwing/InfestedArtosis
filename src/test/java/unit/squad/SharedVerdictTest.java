package unit.squad;

import config.Config;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedVerdictTest {

    private static final int NOW = 5300;
    private static final BiPredicate<Squad, Squad> ALWAYS_NEAR = (first, second) -> true;
    private static final BiPredicate<Squad, Squad> NEVER_NEAR = (first, second) -> false;

    private static Squad squad(SquadStatus status) {
        Squad squad = new GroundSquad();
        squad.setStatus(status);
        return squad;
    }

    private static List<Squad> reconcile(List<Squad> squads, BiPredicate<Squad, Squad> near) {
        return SharedVerdict.reconcile(squads, Collections.emptyList(), near, NOW, squad -> { }, squad -> { });
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
            Squad fighter = squad(SquadStatus.FIGHT);
            Squad retreater = squad(SquadStatus.RETREAT);
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
    void threeNeighboursEndOnOneStatus() {
        List<Squad> squads = new ArrayList<>(Arrays.asList(squad(SquadStatus.FIGHT), squad(SquadStatus.RETREAT),
                squad(SquadStatus.FIGHT)));

        reconcile(squads, ALWAYS_NEAR);

        assertEquals(0, SharedVerdict.countOpposed(squads, ALWAYS_NEAR));
    }

    @Test
    void aFightLockOnTheFightingSideHoldsTheNeighbourInFight() {
        Squad fighter = squad(SquadStatus.FIGHT);
        fighter.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT);

        reconcile(Arrays.asList(fighter, retreater), ALWAYS_NEAR);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertTrue(retreater.isFightLocked(NOW + 1));
        assertFalse(retreater.isRetreatLocked(NOW + 1));
    }

    @Test
    void aRetreatLockOnTheRetreatingSideHoldsTheNeighbourInRetreat() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);
        retreater.startRetreatLock(NOW);

        reconcile(Arrays.asList(fighter, retreater), ALWAYS_NEAR);

        assertEquals(SquadStatus.RETREAT, fighter.getStatus());
        assertTrue(fighter.isRetreatLocked(NOW + 1));
    }

    @Test
    void withoutLocksOrSupplyTheTieGoesToRetreat() {
        assertFalse(SharedVerdict.fightWins(false, false, 10, 10));
        assertTrue(SharedVerdict.fightWins(false, false, 11, 10));
        assertFalse(SharedVerdict.fightWins(false, false, 9, 10));
        assertTrue(SharedVerdict.fightWins(true, false, 1, 50));
        assertFalse(SharedVerdict.fightWins(false, true, 50, 1));
        assertTrue(SharedVerdict.fightWins(true, true, 11, 10));
    }

    @Test
    void squadsOutsideTheRadiusKeepTheirOwnStatus() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);

        List<Squad> turned = reconcile(Arrays.asList(fighter, retreater), NEVER_NEAR);

        assertTrue(turned.isEmpty());
        assertEquals(SquadStatus.FIGHT, fighter.getStatus());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
    }

    @Test
    void aFrozenSquadKeepsItsStatus() {
        Squad fighter = squad(SquadStatus.FIGHT);
        Squad retreater = squad(SquadStatus.RETREAT);

        SharedVerdict.reconcile(Arrays.asList(fighter, retreater), Collections.singletonList(retreater), ALWAYS_NEAR,
                NOW, squad -> { }, squad -> { });

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
    void theSwitchIsOnByDefault() {
        assertTrue(Config.squadSharedVerdict);
    }

    @Test
    void withTheSwitchOffTheFightLockDecisionIsMainsAndTheNeighbourLockIsIgnored() {
        boolean saved = Config.squadSharedVerdict;
        try {
            Config.squadSharedVerdict = false;
            Squad locked = squad(SquadStatus.FIGHT);
            locked.startFightLock(NOW);
            Squad neighbour = squad(SquadStatus.FIGHT);
            List<Squad> squads = Arrays.asList(locked, neighbour);

            boolean onMain = neighbour.isFightLocked(NOW + 1);
            boolean fightLocked = onMain || Config.squadSharedVerdict
                    && SharedVerdict.neighbourFightLock(neighbour, squads, ALWAYS_NEAR, NOW + 1);

            assertFalse(fightLocked);
            assertFalse(SquadManager.fightLockHolds(fightLocked, CombatSimulator.CombatResult.RETREAT, true, 1.0,
                    1.3));
        } finally {
            Config.squadSharedVerdict = saved;
        }
    }
}
