package unit.squad;

import config.Config;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.FightBand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedVerdictTest {

    private static final int NOW = 5300;
    private static final BiPredicate<Squad, Squad> ALWAYS_NEAR = (first, second) -> true;
    private static final BiPredicate<Squad, Squad> NEVER_NEAR = (first, second) -> false;
    private static final double RETREAT_THRESHOLD = 0.7;
    private static final double ENGAGE_THRESHOLD = 1.3;

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

    private static SquadManager.SharedOutcome share(List<Squad> squads, Set<Squad> simulated, Set<Squad> leaving,
                                                    BiPredicate<Squad, Squad> near, int now) {
        return SquadManager.shareVerdicts(SharedVerdict.Exclusion.NONE, squads, simulated, leaving, near, now,
                squad -> { }, squad -> { });
    }

    private static SquadManager.SharedOutcome share(List<Squad> squads, BiPredicate<Squad, Squad> near, int now) {
        return share(squads, new HashSet<>(squads), Collections.emptySet(), near, now);
    }

    private static SquadManager.SharedOutcome share(Squad... squads) {
        return share(Arrays.asList(squads), ALWAYS_NEAR, NOW);
    }

    private static void decideRetreat(Squad squad, int now) {
        squad.setStatus(SquadStatus.RETREAT);
        squad.startRetreatLock(now);
    }

    @Test
    void twoNeighboursNeverHoldOppositeStatusesAfterTheDecisionStep() {
        Squad fighter = squad(SquadStatus.FIGHT, 6);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        SquadManager.SharedOutcome outcome = share(fighter, retreater);

        assertTrue(outcome.opposed.isEmpty());
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

            SquadManager.SharedOutcome outcome = share(fighter, retreater);

            assertTrue(outcome.opposed.isEmpty(), "mask " + mask);
        }
    }

    @Test
    void aChainOfSquadsSettlesOnOneStatusTurningEachOnceAndStaysThereOnTheNextFrame() {
        Squad a = squad(SquadStatus.FIGHT, 10);
        a.startFightLock(NOW);
        Squad b = squad(SquadStatus.RETREAT, 5);
        Squad c = squad(SquadStatus.RETREAT, 20);
        c.startRetreatLock(NOW);
        List<Squad> squads = Arrays.asList(a, b, c);
        BiPredicate<Squad, Squad> chain = (first, second) -> first != second && (first == b || second == b);

        SquadManager.SharedOutcome first = share(squads, chain, NOW);
        SquadManager.SharedOutcome second = share(squads, chain, NOW + 1);

        assertTrue(first.opposed.isEmpty());
        assertTrue(second.turns.isEmpty());
        assertEquals(first.turns.size(), first.turns.stream().map(turn -> turn.squad).distinct().count());
        assertEquals(a.getStatus(), b.getStatus());
        assertEquals(b.getStatus(), c.getStatus());
    }

    @Test
    void theHeavierSideWinsOnSupplyWhenNeitherSideHoldsALock() {
        Squad fighter = squad(SquadStatus.FIGHT, 12);
        Squad retreater = squad(SquadStatus.RETREAT, 8);

        SquadManager.SharedOutcome outcome = share(fighter, retreater);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertEquals(SharedVerdict.Basis.SUPPLY, outcome.turns.get(0).basis);
        assertSame(fighter, outcome.turns.get(0).decider);
    }

    @Test
    void aLockThatTurnsTheSupplyComparisonIsNamedAsTheBasis() {
        Squad fighter = squad(SquadStatus.FIGHT, 6);
        fighter.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 8);

        SquadManager.SharedOutcome outcome = share(fighter, retreater);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertEquals(SharedVerdict.Basis.LOCK, outcome.turns.get(0).basis);
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
        Squad retreater = squad(SquadStatus.RETREAT, 8);

        share(fighter, retreater);

        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
    }

    @Test
    void aSquadThatDidNotRunTheSimIsFrozenAndPullsItsGroup() {
        Squad committed = squad(SquadStatus.FIGHT, 4);
        Squad retreater = squad(SquadStatus.RETREAT, 30);
        retreater.startRetreatLock(NOW);
        List<Squad> squads = Arrays.asList(committed, retreater);

        SquadManager.SharedOutcome outcome = share(squads, Collections.singleton(retreater),
                Collections.emptySet(), ALWAYS_NEAR, NOW);

        assertEquals(SquadStatus.FIGHT, committed.getStatus());
        assertEquals(SquadStatus.FIGHT, retreater.getStatus());
        assertEquals(SharedVerdict.Basis.FROZEN, outcome.turns.get(0).basis);
        assertSame(committed, outcome.turns.get(0).decider);
    }

    @Test
    void aCorneredHoldFreezesTheSquadButACollapseFreeUnheldSquadIsNot() {
        Squad cornered = squad(SquadStatus.FIGHT, 4);
        cornered.holdCorneredFight(NOW);
        Set<Squad> simulated = new HashSet<>(Collections.singletonList(cornered));
        Squad plain = squad(SquadStatus.FIGHT, 4);
        simulated.add(plain);

        assertTrue(SquadManager.frozenForVerdict(cornered, simulated, NOW));
        assertFalse(SquadManager.frozenForVerdict(plain, simulated, NOW));
        assertTrue(SquadManager.frozenForVerdict(plain, Collections.emptySet(), NOW));
    }

    @Test
    void frozenSquadsThatDisagreeLeaveTheGroupAloneAndThePairNamesWhy() {
        Squad fighter = squad(SquadStatus.FIGHT, 4);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        List<Squad> squads = Arrays.asList(fighter, retreater);

        SquadManager.SharedOutcome outcome = share(squads, Collections.emptySet(), Collections.emptySet(),
                ALWAYS_NEAR, NOW);

        assertTrue(outcome.turns.isEmpty());
        assertEquals(1, outcome.opposed.size());
        assertEquals(SharedVerdict.Exclusion.FROZEN_SPLIT, outcome.opposed.get(0).exclusion);
    }

    @Test
    void aSquadReadingBelowItsRetreatThresholdIsNeverTurnedToFightEvenByAFrozenOrLockedNeighbour() {
        Squad frozenFighter = squad(SquadStatus.FIGHT, 30);
        frozenFighter.holdCorneredFight(NOW);
        frozenFighter.startFightLock(NOW);
        Squad leaving = squad(SquadStatus.RETREAT, 4);
        decideRetreat(leaving, NOW);
        List<Squad> squads = Arrays.asList(frozenFighter, leaving);

        SquadManager.SharedOutcome outcome = share(squads, new HashSet<>(squads), Collections.singleton(leaving),
                ALWAYS_NEAR, NOW);

        assertTrue(outcome.turns.isEmpty());
        assertEquals(SquadStatus.RETREAT, leaving.getStatus());
        assertEquals(SharedVerdict.Exclusion.BELOW_RELEASE, outcome.opposed.get(0).exclusion);
    }

    private static boolean leaves(boolean band, boolean inContact, double ratio) {
        double hold = FightBand.holdThreshold(inContact, RETREAT_THRESHOLD, ENGAGE_THRESHOLD);
        return SquadManager.leavesFight(band, CombatSimulator.CombatResult.RETREAT, true, ratio, ENGAGE_THRESHOLD,
                hold);
    }

    @Test
    void aSquadLeavesOnAMeasuredRetreatBelowTheThresholdItsFightLockIsReleasedAt() {
        assertTrue(leaves(true, true, 0.69));
        assertFalse(leaves(true, true, 0.8));
        assertTrue(leaves(true, false, 0.9));
        assertFalse(leaves(true, false, 1.0));
        assertTrue(leaves(false, true, 1.1));
        assertFalse(leaves(false, true, ENGAGE_THRESHOLD));
    }

    @Test
    void onlyAMeasuredRetreatReadCountsAsLeaving() {
        CombatSimulator.CombatResult retreat = CombatSimulator.CombatResult.RETREAT;
        assertTrue(SharedVerdict.leaves(retreat, true, 0.69, RETREAT_THRESHOLD));
        assertFalse(SharedVerdict.leaves(retreat, true, RETREAT_THRESHOLD, RETREAT_THRESHOLD));
        assertFalse(SharedVerdict.leaves(retreat, false, 0.1, RETREAT_THRESHOLD));
        assertFalse(SharedVerdict.leaves(CombatSimulator.CombatResult.ADVANCE, true, 0, RETREAT_THRESHOLD));
        assertFalse(SquadManager.leavesFight(true, retreat, true, 0, 0, 0));
    }

    @Test
    void aRetreatingSquadReadingInsideTheBandOutOfContactIsNotTurnedOnEitherFrame() {
        Squad locked = squad(SquadStatus.FIGHT, 20);
        locked.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        decideRetreat(retreater, NOW);
        List<Squad> squads = Arrays.asList(locked, retreater);
        Set<Squad> leaving = leaves(true, false, 0.9) ? Collections.singleton(retreater) : Collections.emptySet();

        SquadManager.SharedOutcome first = share(squads, new HashSet<>(squads), leaving, ALWAYS_NEAR, NOW);
        SquadManager.SharedOutcome second = share(squads, new HashSet<>(squads), leaving, ALWAYS_NEAR, NOW + 1);

        assertTrue(first.turns.isEmpty());
        assertTrue(second.turns.isEmpty());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
        assertTrue(retreater.isRetreatLocked(NOW + 1));
    }

    @Test
    void aNeighbourHoldIsNamedOnlyWhenNothingOfTheSquadsOwnHeldIt() {
        assertEquals(telemetry.SquadLock.NEIGHBOUR_FIGHT, SquadManager.neighbourHoldLock(true, false, false, true));
        assertEquals(telemetry.SquadLock.NEIGHBOUR_FIGHT, SquadManager.neighbourHoldLock(true, false, true, true));
        assertEquals(telemetry.SquadLock.COMMIT_WINDOW, SquadManager.neighbourHoldLock(true, true, false, false));
        assertEquals(telemetry.SquadLock.FIGHT, SquadManager.neighbourHoldLock(true, true, true, true));
        assertEquals(telemetry.SquadLock.FIGHT, SquadManager.neighbourHoldLock(false, false, true, true));
        assertEquals(telemetry.SquadLock.FIGHT_LOCK_BAND, SquadManager.neighbourHoldLock(false, false, false, true));
    }

    @Test
    void aSquadTurnedToRetreatLosesItsTurnBackCommit() {
        Squad retreater = squad(SquadStatus.RETREAT, 20);
        retreater.startRetreatLock(NOW);
        Squad committed = squad(SquadStatus.FIGHT, 2);
        committed.startTurnBackCommit(NOW);

        share(retreater, committed);

        assertEquals(SquadStatus.RETREAT, committed.getStatus());
        assertFalse(committed.isTurnBackCommitted(NOW + 1));
    }

    @Test
    void aSquadTurnedToFightThatReadsBelowItsRetreatThresholdNextFrameIsNotTurnedAgain() {
        Squad locked = squad(SquadStatus.FIGHT, 10);
        locked.startFightLock(NOW);
        Squad turned = squad(SquadStatus.RETREAT, 4);
        List<Squad> squads = Arrays.asList(locked, turned);

        SquadManager.SharedOutcome first = share(squads, ALWAYS_NEAR, NOW);
        assertEquals(1, first.turns.size());
        assertEquals(SquadStatus.FIGHT, turned.getStatus());

        decideRetreat(turned, NOW + 1);
        SquadManager.SharedOutcome second = share(squads, new HashSet<>(squads), Collections.singleton(turned),
                ALWAYS_NEAR, NOW + 1);

        assertTrue(second.turns.isEmpty());
        assertEquals(SquadStatus.RETREAT, turned.getStatus());
    }

    @Test
    void aSquadTurnedToFightThatLeavesInsideTheBandNextFrameIsNotTurnedAgainWhileItHoldsTheLock() {
        Squad locked = squad(SquadStatus.FIGHT, 10);
        locked.startFightLock(NOW);
        Squad turned = squad(SquadStatus.RETREAT, 4);
        List<Squad> squads = Arrays.asList(locked, turned);
        share(squads, ALWAYS_NEAR, NOW);
        int adopted = turned.getFightLockedUntilFrame();

        decideRetreat(turned, NOW + 1);
        SquadManager.SharedOutcome second = share(squads, ALWAYS_NEAR, NOW + 1);
        SquadManager.SharedOutcome expired = share(squads, ALWAYS_NEAR, adopted);

        assertTrue(second.turns.isEmpty());
        assertEquals(SharedVerdict.Exclusion.LOCK_BROKEN, second.opposed.get(0).exclusion);
        assertEquals(1, expired.turns.size());
    }

    @Test
    void aSquadTurnedToFightAdoptsTheWinningSidesLockAndDropsItsRetreatLock() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        fighter.startFightLock(NOW - 10);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        retreater.startRetreatLock(NOW);

        share(fighter, retreater);

        assertEquals(fighter.getFightLockedUntilFrame(), retreater.getFightLockedUntilFrame());
        assertFalse(retreater.isRetreatLocked(NOW));
    }

    @Test
    void aSquadTurnedToFightBySupplyAloneGetsAFreshFightLock() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        share(fighter, retreater);

        assertTrue(retreater.isFightLocked(NOW + 1));
        assertFalse(fighter.isFightLocked(NOW + 1));
    }

    @Test
    void aSquadTurnedToRetreatWhileTheRetreatingSideHoldsNoLockGetsAFreshRetreatLockAndStaysOnTheNextFrame() {
        Squad retreater = squad(SquadStatus.RETREAT, 20);
        retreater.startRetreatLock(NOW - 200);
        Squad advancing = squad(SquadStatus.FIGHT, 2);
        List<Squad> squads = Arrays.asList(retreater, advancing);

        SquadManager.SharedOutcome first = share(squads, ALWAYS_NEAR, NOW);
        SquadManager.SharedOutcome second = share(squads, ALWAYS_NEAR, NOW + 1);

        assertEquals(1, first.turns.size());
        assertEquals(SquadStatus.RETREAT, advancing.getStatus());
        assertEquals(NOW + Squad.GROUND_RETREAT_LOCK_FRAMES, advancing.getRetreatLockedUntilFrame());
        assertTrue(advancing.isRetreatLocked(NOW + 1));
        assertTrue(second.turns.isEmpty());
    }

    @Test
    void aSquadTurnedToRetreatAdoptsTheRetreatingSidesLockAndGateAndDropsItsFightLock() {
        Squad retreater = squad(SquadStatus.RETREAT, 20);
        retreater.startRetreatLock(NOW - 50);
        retreater.noteMeasuredRetreat(NOW - 50);
        Squad fighter = squad(SquadStatus.FIGHT, 2);
        fighter.startFightLock(NOW);

        share(retreater, fighter);

        assertEquals(SquadStatus.RETREAT, fighter.getStatus());
        assertEquals(retreater.getRetreatLockedUntilFrame(), fighter.getRetreatLockedUntilFrame());
        assertEquals(retreater.getBlindTurnBackGateUntilFrame(), fighter.getBlindTurnBackGateUntilFrame());
        assertFalse(fighter.isFightLocked(NOW));
    }

    @Test
    void aGatedRetreaterWaitsForAHeldFightSide() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        Squad gated = squad(SquadStatus.RETREAT, 4);
        gated.noteMeasuredRetreat(NOW - 30);

        SquadManager.SharedOutcome unheld = share(fighter, gated);
        assertTrue(unheld.turns.isEmpty());
        assertEquals(SharedVerdict.Exclusion.BLIND_GATE, unheld.opposed.get(0).exclusion);

        fighter.startFightLock(NOW);
        SquadManager.SharedOutcome held = share(fighter, gated);
        assertEquals(1, held.turns.size());
        assertEquals(SquadStatus.FIGHT, gated.getStatus());
    }

    @Test
    void squadsOutsideTheRadiusKeepTheirOwnStatus() {
        Squad fighter = squad(SquadStatus.FIGHT, 4);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        SquadManager.SharedOutcome outcome = share(Arrays.asList(fighter, retreater), NEVER_NEAR, NOW);

        assertTrue(outcome.turns.isEmpty());
        assertTrue(outcome.opposed.isEmpty());
        assertEquals(SquadStatus.RETREAT, retreater.getStatus());
    }

    @Test
    void airSquadsAndNonDecisionStatusesAreNotPaired() {
        Squad air = new AirSquad();
        air.setStatus(SquadStatus.RETREAT);
        Squad fighter = squad(SquadStatus.FIGHT, 4);
        Squad rallying = squad(SquadStatus.RALLY, 4);

        SquadManager.SharedOutcome outcome = share(air, fighter, rallying);

        assertTrue(outcome.turns.isEmpty());
        assertEquals(SquadStatus.RETREAT, air.getStatus());
        assertEquals(0, SharedVerdict.countOpposed(Arrays.asList(air, fighter, rallying), ALWAYS_NEAR));
    }

    @Test
    void withTheSwitchOffOrAStormNothingTurnsAndThePairNamesWhy() {
        assertEquals(SharedVerdict.Exclusion.SWITCH_OFF, SquadManager.verdictSkipped(false, false));
        assertEquals(SharedVerdict.Exclusion.SWITCH_OFF, SquadManager.verdictSkipped(false, true));
        assertEquals(SharedVerdict.Exclusion.STORM, SquadManager.verdictSkipped(true, true));
        assertEquals(SharedVerdict.Exclusion.NONE, SquadManager.verdictSkipped(true, false));

        for (SharedVerdict.Exclusion skipped : Arrays.asList(SharedVerdict.Exclusion.SWITCH_OFF,
                SharedVerdict.Exclusion.STORM)) {
            Squad fighter = squad(SquadStatus.FIGHT, 20);
            fighter.startFightLock(NOW);
            Squad retreater = squad(SquadStatus.RETREAT, 4);
            List<Squad> squads = Arrays.asList(fighter, retreater);
            List<Squad> turned = new ArrayList<>();

            SquadManager.SharedOutcome outcome = SquadManager.shareVerdicts(skipped, squads, new HashSet<>(squads),
                    Collections.emptySet(), ALWAYS_NEAR, NOW, turned::add, turned::add);

            assertTrue(outcome.turns.isEmpty());
            assertTrue(turned.isEmpty());
            assertEquals(SquadStatus.RETREAT, retreater.getStatus());
            assertEquals(skipped, outcome.opposed.get(0).exclusion);
        }
    }

    @Test
    void theCallbacksRunOnceForEachTurnedSquadWithItsNewStatusSet() {
        Squad fighter = squad(SquadStatus.FIGHT, 20);
        Squad retreater = squad(SquadStatus.RETREAT, 4);
        Squad other = squad(SquadStatus.RETREAT, 2);
        List<Squad> squads = Arrays.asList(fighter, retreater, other);
        List<SquadStatus> seen = new ArrayList<>();

        SquadManager.shareVerdicts(SharedVerdict.Exclusion.NONE, squads, new HashSet<>(squads),
                Collections.emptySet(), ALWAYS_NEAR, NOW, squad -> seen.add(squad.getStatus()),
                squad -> seen.add(SquadStatus.RETREAT));

        assertEquals(Arrays.asList(SquadStatus.FIGHT, SquadStatus.FIGHT), seen);
    }

    @Test
    void aNeighbourFightLockHoldsAFightSquad() {
        Squad locked = squad(SquadStatus.FIGHT, 4);
        locked.startFightLock(NOW);
        Squad neighbour = squad(SquadStatus.FIGHT, 4);
        List<Squad> squads = Arrays.asList(locked, neighbour);

        assertTrue(SharedVerdict.neighbourFightLock(neighbour, squads, ALWAYS_NEAR, NOW + 1));
        assertFalse(SharedVerdict.neighbourFightLock(neighbour, squads, NEVER_NEAR, NOW + 1));
        assertFalse(SharedVerdict.neighbourFightLock(neighbour, squads, ALWAYS_NEAR, NOW + 10_000));
        assertFalse(SharedVerdict.neighbourFightLock(locked, Collections.singletonList(locked), ALWAYS_NEAR, NOW + 1));
    }

    @Test
    void aNeighbourFightLockDoesNotHoldARetreatingSquad() {
        Squad locked = squad(SquadStatus.FIGHT, 4);
        locked.startFightLock(NOW);
        Squad retreater = squad(SquadStatus.RETREAT, 4);

        assertFalse(SharedVerdict.neighbourFightLock(retreater, Arrays.asList(locked, retreater), ALWAYS_NEAR,
                NOW + 1));
    }

    @Test
    void aNeighbourFightLockNeverHoldsAReadBelowTheRetreatThreshold() {
        boolean saved = Config.squadSharedVerdict;
        try {
            Config.squadSharedVerdict = true;
            Squad locked = squad(SquadStatus.FIGHT, 4);
            locked.startFightLock(NOW);
            Squad neighbour = squad(SquadStatus.FIGHT, 4);
            boolean held = SquadManager.neighbourLockApplies(neighbour, Arrays.asList(locked, neighbour), ALWAYS_NEAR,
                    NOW + 1);
            double inContact = FightBand.holdThreshold(true, RETREAT_THRESHOLD, ENGAGE_THRESHOLD);
            double release = FightBand.fightLockReleaseThreshold(true, ENGAGE_THRESHOLD, inContact);

            assertTrue(held);
            assertTrue(SquadManager.fightLockHolds(held, CombatSimulator.CombatResult.RETREAT, true, 0.8, release));
            assertFalse(SquadManager.fightLockHolds(held, CombatSimulator.CombatResult.RETREAT, true, 0.69, release));
            for (boolean contact : new boolean[] {true, false}) {
                double hold = FightBand.holdThreshold(contact, RETREAT_THRESHOLD, ENGAGE_THRESHOLD);
                for (double ratio : new double[] {0.69, 0.8, 0.9, 1.0, 1.2}) {
                    assertEquals(!SquadManager.fightLockHolds(true, CombatSimulator.CombatResult.RETREAT, true, ratio,
                            FightBand.fightLockReleaseThreshold(true, ENGAGE_THRESHOLD, hold)),
                            leaves(true, contact, ratio), "contact " + contact + " ratio " + ratio);
                }
            }
        } finally {
            Config.squadSharedVerdict = saved;
        }
    }

    @Test
    void theSwitchGatesTheNeighbourLockInTheProductionHelper() {
        boolean saved = Config.squadSharedVerdict;
        try {
            Squad locked = squad(SquadStatus.FIGHT, 4);
            locked.startFightLock(NOW);
            Squad neighbour = squad(SquadStatus.FIGHT, 4);
            List<Squad> squads = Arrays.asList(locked, neighbour);

            Config.squadSharedVerdict = false;
            assertFalse(SquadManager.neighbourLockApplies(neighbour, squads, ALWAYS_NEAR, NOW + 1));

            Config.squadSharedVerdict = true;
            assertTrue(SquadManager.neighbourLockApplies(neighbour, squads, ALWAYS_NEAR, NOW + 1));
        } finally {
            Config.squadSharedVerdict = saved;
        }
    }
}
