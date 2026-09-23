package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;
import util.Arc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.SquadManager.ContainmentVerdict;
import static unit.squad.SquadManager.OutrangedHit;
import static unit.squad.SquadManager.ReinforcementPath;
import static unit.squad.SquadManager.containKillRadius;
import static unit.squad.SquadManager.containmentExitPath;
import static unit.squad.SquadManager.containmentVerdict;
import static unit.squad.SquadManager.creditContainKill;
import static unit.squad.SquadManager.isCombatThreat;
import static unit.squad.SquadManager.isContainmentThrottled;
import static unit.squad.SquadManager.mayEnterContainment;
import static unit.squad.SquadManager.mergeEndsContainment;
import static unit.squad.SquadManager.reinforcementPath;
import static unit.squad.SquadManager.threatensContainment;

class SquadContainmentTest {

    private static final boolean BASES_SAFE = false;
    private static final boolean BASES_ATTACKED = true;
    private static final boolean HOLDING_UP = false;
    private static final boolean BLEEDING = true;
    private static final boolean ARC_KEPT = false;
    private static final boolean ARC_LOST = true;
    private static final boolean UNTHROTTLED = false;
    private static final boolean THROTTLED = true;
    private static final boolean ENEMIES_ON_ARC = true;
    private static final boolean ARC_CLEAR = false;
    private static final boolean IN_TIME = false;
    private static final boolean TIMED_OUT = true;
    private static final boolean CAN_BREAK = true;
    private static final boolean BELOW_BREAK_RATIO = false;
    private static final boolean SHOULD_CONTAIN = true;
    private static final List<UnitType> PROBE = Collections.singletonList(UnitType.Protoss_Probe);
    private static final List<UnitType> ZEALOT = Collections.singletonList(UnitType.Protoss_Zealot);
    private static final int FLAP_WINDOW = 24;
    private static final int REPLAY_START = 18150;
    private static final int KILL_FRAME = 18150;
    private static final int SQUAD_SUPPLY_LEFT = 6;
    private static final OutrangedHit NO_HIT = OutrangedHit.NONE;
    private static final OutrangedHit HIT_ARC_KEPT = OutrangedHit.ARC_KEPT;
    private static final OutrangedHit HIT_ARC_LOST = OutrangedHit.ARC_LOST;

    private static ContainmentVerdict verdict(boolean basesUnderAttack, boolean throttled, boolean engaged,
                                              boolean timedOut, boolean canBreak, boolean shouldContain) {
        return containmentVerdict(basesUnderAttack, HOLDING_UP, NO_HIT, throttled, engaged, timedOut, canBreak,
                shouldContain);
    }

    @Test
    void enemiesOnTheArcBelowTheBreakRatioKeepTheContainment() {
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, THROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void anEnemyOnTheArcHoldsThePositionRatherThanRepositioning() {
        assertEquals(ContainmentVerdict.HOLD,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.REPOSITION,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void enemiesInsideTheContainedAreaDoNotEndTheEpisode() {
        assertEquals(ContainmentVerdict.REPOSITION,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void aboveTheBreakRatioTheArmyPushesIn() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void onlyTheStrengthGateCommitsTheSquad() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, CAN_BREAK, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void theTimeoutDisengagesOnlyTheSquadThatRanOut() {
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, TIMED_OUT, CAN_BREAK, SHOULD_CONTAIN));
    }

    @Test
    void containmentNoLongerApplicableFallsThroughToRetreat() {
        assertEquals(ContainmentVerdict.RETREAT,
                verdict(BASES_SAFE, UNTHROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, false));
    }

    @Test
    void aThrottledFrameWithSafeBasesKeepsTheArcUnlessTheSquadBleedsOrLosesItsArc() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                verdict(BASES_ATTACKED, THROTTLED, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
            for (boolean timedOut : new boolean[] {IN_TIME, TIMED_OUT}) {
                for (boolean canBreak : new boolean[] {BELOW_BREAK_RATIO, CAN_BREAK}) {
                    for (boolean shouldContain : new boolean[] {SHOULD_CONTAIN, false}) {
                        assertEquals(ContainmentVerdict.HOLD,
                                verdict(BASES_SAFE, THROTTLED, engaged, timedOut, canBreak, shouldContain),
                                "a throttled frame with safe bases must keep the arc");
                    }
                }
            }
        }
    }

    @Test
    void attritionRetreatsTheSquadOnAnyFrameIncludingAThrottledOne() {
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
                for (boolean canBreak : new boolean[] {BELOW_BREAK_RATIO, CAN_BREAK}) {
                    assertEquals(ContainmentVerdict.RETREAT,
                            containmentVerdict(BASES_SAFE, BLEEDING, NO_HIT, throttled, engaged, IN_TIME,
                                    canBreak, SHOULD_CONTAIN));
                }
            }
        }
    }

    @Test
    void withoutAttritionAnEngagedSquadHolds() {
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, HOLDING_UP, NO_HIT, UNTHROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.HOLD,
                containmentVerdict(BASES_SAFE, HOLDING_UP, NO_HIT, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void aBaseUnderAttackStillOutranksAttrition() {
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_ATTACKED, BLEEDING, HIT_ARC_LOST, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void noArcPointOutOfReachRetreatsInsteadOfHolding() {
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            assertEquals(ContainmentVerdict.RETREAT,
                    containmentVerdict(BASES_SAFE, HOLDING_UP, HIT_ARC_LOST, throttled, ENEMIES_ON_ARC, IN_TIME,
                            BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        }
    }

    @Test
    void anOutrangedHitOnAThrottledFramePushesBackOrRetreatsAndNeverHolds() {
        for (boolean engaged : new boolean[] {ARC_CLEAR, ENEMIES_ON_ARC}) {
            for (boolean timedOut : new boolean[] {IN_TIME, TIMED_OUT}) {
                for (boolean shouldContain : new boolean[] {SHOULD_CONTAIN, false}) {
                    assertEquals(ContainmentVerdict.PUSH_BACK,
                            containmentVerdict(BASES_SAFE, HOLDING_UP, HIT_ARC_KEPT, THROTTLED, engaged, timedOut,
                                    BELOW_BREAK_RATIO, shouldContain));
                    assertEquals(ContainmentVerdict.RETREAT,
                            containmentVerdict(BASES_SAFE, HOLDING_UP, HIT_ARC_LOST, THROTTLED, engaged, timedOut,
                                    BELOW_BREAK_RATIO, shouldContain));
                }
            }
        }
    }

    @Test
    void anOutrangedHitOverridesAnEnemyOnTheArc() {
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            assertEquals(ContainmentVerdict.HOLD,
                    containmentVerdict(BASES_SAFE, HOLDING_UP, NO_HIT, throttled, ENEMIES_ON_ARC, IN_TIME,
                            BELOW_BREAK_RATIO, SHOULD_CONTAIN));
            assertEquals(ContainmentVerdict.PUSH_BACK,
                    containmentVerdict(BASES_SAFE, HOLDING_UP, HIT_ARC_KEPT, throttled, ENEMIES_ON_ARC, IN_TIME,
                            BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        }
    }

    @Test
    void attritionAndABaseUnderAttackOutrankAnOutrangedHit() {
        assertEquals(ContainmentVerdict.RETREAT,
                containmentVerdict(BASES_SAFE, BLEEDING, HIT_ARC_KEPT, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        assertEquals(ContainmentVerdict.BREAK_ALL,
                containmentVerdict(BASES_ATTACKED, HOLDING_UP, HIT_ARC_KEPT, THROTTLED, ENEMIES_ON_ARC, IN_TIME,
                        BELOW_BREAK_RATIO, SHOULD_CONTAIN));
    }

    @Test
    void anOutrangedHitKeepsTheArcUnlessNoPointIsLeft() {
        assertEquals(NO_HIT, SquadManager.outrangedHitVerdict(false, false));
        assertEquals(NO_HIT, SquadManager.outrangedHitVerdict(false, true));
        assertEquals(HIT_ARC_KEPT, SquadManager.outrangedHitVerdict(true, false));
        assertEquals(HIT_ARC_LOST, SquadManager.outrangedHitVerdict(true, true));
    }

    @Test
    void eachContainmentExitNamesItsCause() {
        assertEquals(DecisionPath.CONTAIN_ATTRITION, containmentExitPath(BLEEDING, ARC_KEPT));
        assertEquals(DecisionPath.CONTAIN_OUTRANGED, containmentExitPath(HOLDING_UP, ARC_LOST));
        assertEquals(DecisionPath.CONTAIN_RETREAT, containmentExitPath(HOLDING_UP, ARC_KEPT));
    }

    @Test
    void aUnitJoiningAContainingSquadLeavesItContaining() {
        assertEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(SquadStatus.CONTAIN, false));
        assertEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(SquadStatus.CONTAIN, true));
    }

    @Test
    void onlyAContainingSquadSkipsTheSim() {
        for (SquadStatus status : SquadStatus.values()) {
            if (status == SquadStatus.CONTAIN) {
                continue;
            }
            assertNotEquals(ReinforcementPath.JOIN_CONTAINMENT, reinforcementPath(status, false));
        }
        assertEquals(ReinforcementPath.STAGE, reinforcementPath(SquadStatus.RALLY, true));
        assertEquals(ReinforcementPath.SIMULATE, reinforcementPath(SquadStatus.RALLY, false));
        assertEquals(ReinforcementPath.SIMULATE, reinforcementPath(SquadStatus.FIGHT, false));
    }

    @Test
    void aMergeOutOfContainClosesTheContainingSourcesEpisode() {
        assertTrue(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.FIGHT));
        assertTrue(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.RUNBY));
    }

    @Test
    void aMergeThatStaysInContainOrNeverContainedClosesNothing() {
        assertFalse(mergeEndsContainment(SquadStatus.CONTAIN, SquadStatus.CONTAIN));
        assertFalse(mergeEndsContainment(SquadStatus.FIGHT, SquadStatus.FIGHT));
        assertFalse(mergeEndsContainment(SquadStatus.RALLY, SquadStatus.CONTAIN));
    }

    @Test
    void aKillAtTheOldEngageRadiusIsNotCreditedToALingContain() {
        int lingReach = EnemyReachMemory.baseGroundRange(UnitType.Zerg_Zergling);

        assertTrue(containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine) < 256);
    }

    @Test
    void aKillBesideAMemberIsCredited() {
        int lingReach = EnemyReachMemory.baseGroundRange(UnitType.Zerg_Zergling);
        int adjacent = UnitType.Zerg_Zergling.dimensionRight() + UnitType.Terran_Marine.dimensionLeft() + lingReach;

        assertTrue(adjacent <= containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine));
    }

    @Test
    void aRangedMemberIsCreditedFartherOut() {
        int lingReach = EnemyReachMemory.baseGroundRange(UnitType.Zerg_Zergling);
        int hydraReach = EnemyReachMemory.baseGroundRange(UnitType.Zerg_Hydralisk);

        assertTrue(containKillRadius(UnitType.Zerg_Hydralisk, hydraReach, UnitType.Terran_Marine)
                > containKillRadius(UnitType.Zerg_Zergling, lingReach, UnitType.Terran_Marine));
    }

    @Test
    void aBaseUnderAttackRefusesContainmentEntry() {
        assertFalse(mayEnterContainment(BASES_ATTACKED, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
        assertTrue(mayEnterContainment(BASES_SAFE, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
    }

    @Test
    void containmentEntryStillNeedsShouldContainAndAClosedStrengthGate() {
        assertFalse(mayEnterContainment(BASES_SAFE, false, BELOW_BREAK_RATIO));
        assertFalse(mayEnterContainment(BASES_SAFE, SHOULD_CONTAIN, CAN_BREAK));
    }

    @Test
    void aLoneProbeThreatAllowsEntryAndNeverBreaksAContain() {
        boolean underAttack = threatensContainment(PROBE);

        assertTrue(mayEnterContainment(underAttack, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            assertNotEquals(ContainmentVerdict.BREAK_ALL,
                    verdict(underAttack, throttled, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        }
    }

    @Test
    void aZealotThreatRefusesEntryAndBreaksAContain() {
        boolean underAttack = threatensContainment(ZEALOT);

        assertFalse(mayEnterContainment(underAttack, SHOULD_CONTAIN, BELOW_BREAK_RATIO));
        for (boolean throttled : new boolean[] {UNTHROTTLED, THROTTLED}) {
            assertEquals(ContainmentVerdict.BREAK_ALL,
                    verdict(underAttack, throttled, ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN));
        }
    }

    @Test
    void scoutsBesideACombatThreatStillPutTheBaseUnderAttack() {
        assertTrue(threatensContainment(Arrays.asList(UnitType.Protoss_Probe, UnitType.Zerg_Overlord,
                UnitType.Protoss_Zealot)));
        assertFalse(threatensContainment(Arrays.asList(UnitType.Protoss_Probe, UnitType.Zerg_Overlord,
                UnitType.Protoss_Observer)));
        assertFalse(threatensContainment(Collections.emptyList()));
    }

    @Test
    void mobileGroundAndAirFightersAreCombatThreats() {
        assertTrue(isCombatThreat(UnitType.Terran_Marine));
        assertTrue(isCombatThreat(UnitType.Terran_Vulture));
        assertTrue(isCombatThreat(UnitType.Protoss_Zealot));
        assertTrue(isCombatThreat(UnitType.Zerg_Mutalisk));
        assertTrue(isCombatThreat(UnitType.Terran_Wraith));
    }

    @Test
    void scoutsWorkersAndBuildingsAreNotCombatThreats() {
        assertFalse(isCombatThreat(UnitType.Terran_SCV));
        assertFalse(isCombatThreat(UnitType.Protoss_Probe));
        assertFalse(isCombatThreat(UnitType.Zerg_Overlord));
        assertFalse(isCombatThreat(UnitType.Protoss_Observer));
        assertFalse(isCombatThreat(UnitType.Terran_Bunker));
        assertFalse(isCombatThreat(UnitType.Protoss_Photon_Cannon));
    }

    @Test
    void aSquadRetreatingUnderACombatThreatNeverFlapsBetweenContainAndFight() {
        List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240, frame -> ZEALOT);

        assertFalse(statuses.contains(SquadStatus.CONTAIN), "no arc is taken while a base is under attack");
        assertEquals(0, containFightContainFlaps(statuses));
    }

    @Test
    void aScoutCirclingABaseLeavesAFreshContainInPlace() {
        List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240, frame -> PROBE);

        assertTrue(statuses.stream().allMatch(status -> status == SquadStatus.CONTAIN));
    }

    @Test
    void aCombatThreatArrivingMidContainBreaksAtOnceAndIsNotReentered() {
        List<SquadStatus> statuses = replayContainOnRetreatVerdicts(240,
                frame -> frame > 0 ? ZEALOT : Collections.emptyList());

        assertEquals(SquadStatus.CONTAIN, statuses.get(0));
        assertEquals(SquadStatus.FIGHT, statuses.get(1));
        assertEquals(0, containFightContainFlaps(statuses));
    }

    @Test
    void aKillBetweenTwoSquadsOnOneArcIsCreditedOnlyToTheNearer() {
        Arc shared = new Arc(new Position(1000, 1000), new Position(1000, 1400), 160, 90, 8);
        Squad nearer = containingSquad(shared);
        Squad farther = containingSquad(shared);
        Map<Squad, Double> killers = new HashMap<>();
        killers.put(farther, 60.0);
        killers.put(nearer, 20.0);

        Squad credited = creditContainKill(killers, KILL_FRAME, UnitType.Terran_Marine.supplyRequired());

        assertSame(nearer, credited);
        assertFalse(nearer.getContainmentAttrition().isBleeding(KILL_FRAME, SQUAD_SUPPLY_LEFT));
        assertTrue(farther.getContainmentAttrition().isBleeding(KILL_FRAME, SQUAD_SUPPLY_LEFT),
                "the squad that did not land the kill keeps bleeding");
    }

    @Test
    void aKillWithNoSquadInReachIsCreditedToNone() {
        assertNull(creditContainKill(new HashMap<>(), KILL_FRAME, UnitType.Terran_Marine.supplyRequired()));
    }

    private static Squad containingSquad(Arc arc) {
        Squad squad = new Squad();
        squad.setStatus(SquadStatus.CONTAIN);
        squad.startContainLock(KILL_FRAME - 1);
        squad.setContainmentArc(arc);
        for (int ling = 0; ling < 2; ling++) {
            squad.getContainmentAttrition().recordLoss(KILL_FRAME - 1, UnitType.Zerg_Zergling.supplyRequired());
        }
        return squad;
    }

    /**
     * Replays a squad whose sim returns RETREAT on every frame with the strength gate closed, as squad 97a3b43c did
     * in game LSWLD05O from frame 18150. Entry, the contain lock and its throttle, the base threat predicate and the
     * verdict are the production ones, applied in the order tryEnterContainment, enterContainment,
     * evaluateContainingSquad and endContainment apply them.
     */
    private static List<SquadStatus> replayContainOnRetreatVerdicts(int frames,
                                                                    IntFunction<List<UnitType>> threatsAt) {
        List<SquadStatus> statuses = new ArrayList<>();
        Squad squad = new Squad();
        squad.setStatus(SquadStatus.FIGHT);
        for (int frame = REPLAY_START; frame < REPLAY_START + frames; frame++) {
            boolean underAttack = threatensContainment(threatsAt.apply(frame - REPLAY_START));
            if (squad.getStatus() == SquadStatus.CONTAIN) {
                boolean throttled = isContainmentThrottled(squad, frame);
                ContainmentVerdict verdict = containmentVerdict(underAttack, HOLDING_UP, NO_HIT, throttled,
                        ARC_CLEAR, IN_TIME, BELOW_BREAK_RATIO, SHOULD_CONTAIN);
                if (verdict == ContainmentVerdict.BREAK_ALL) {
                    squad.clearContainStart();
                    squad.setStatus(SquadStatus.FIGHT);
                }
            } else if (mayEnterContainment(underAttack, SHOULD_CONTAIN, BELOW_BREAK_RATIO)) {
                squad.setStatus(SquadStatus.CONTAIN);
                squad.startContainLock(frame);
            } else {
                squad.setStatus(SquadStatus.RETREAT);
            }
            statuses.add(squad.getStatus());
        }
        return statuses;
    }

    private static int containFightContainFlaps(List<SquadStatus> statuses) {
        int flaps = 0;
        for (int frame = 1; frame < statuses.size(); frame++) {
            if (statuses.get(frame - 1) != SquadStatus.CONTAIN || statuses.get(frame) != SquadStatus.FIGHT) {
                continue;
            }
            int end = Math.min(statuses.size(), frame + FLAP_WINDOW + 1);
            if (statuses.subList(frame, end).contains(SquadStatus.CONTAIN)) {
                flaps++;
            }
        }
        return flaps;
    }
}
