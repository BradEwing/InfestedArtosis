package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.UnitStrength;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.AirHarassEvaluator.EntryVerdict;
import static unit.squad.AirHarassEvaluator.ExitReason;

class AirHarassEvaluatorTest {

    private static final int NOW = 12000;
    private static final Position STRIKE = new Position(400, 3700);

    private static Map<UnitType, Integer> mutas(int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(UnitType.Zerg_Mutalisk, count);
        return composition;
    }

    private static AirHarassEvaluator.BaseOption<String> option(String base, Position strike, double heat,
                                                                double containDistance) {
        return new AirHarassEvaluator.BaseOption<>(base, strike, heat, true, containDistance);
    }

    private static AirHarassEvaluator.EntryInput.EntryInputBuilder entry() {
        return AirHarassEvaluator.EntryInput.builder()
                .opponentRace(Race.Terran)
                .composition(mutas(9))
                .healthyMutas(9)
                .basesUnderAttack(false)
                .options(Collections.singletonList(option("main", STRIKE, 90, -1)));
    }

    @Test
    void aFlockStandingInMoreAntiAirThanItToleratesIsNotSentIn() {
        assertEquals(EntryVerdict.DEFENDED,
                AirHarassEvaluator.entryVerdict(entry().flockDefense(3.0).tolerance(2.0).build()));
        assertEquals(EntryVerdict.ENTER,
                AirHarassEvaluator.entryVerdict(entry().flockDefense(2.0).tolerance(2.0).build()));
    }

    @Test
    void theEntryAndTheExitAgreeOnTheFlocksAntiAir() {
        double flockDefense = 3.0;
        double tolerance = 2.0;
        assertEquals(EntryVerdict.DEFENDED, AirHarassEvaluator.entryVerdict(
                entry().flockDefense(flockDefense).tolerance(tolerance).build()));
        assertEquals(AirHarassEvaluator.ExitReason.FLOCK_DEFENDED, AirHarassEvaluator.exitReason(
                exit().flockDefense(flockDefense).tolerance(tolerance).build()));
    }

    private static AirHarassEvaluator.ExitInput.ExitInputBuilder exit() {
        return AirHarassEvaluator.ExitInput.builder()
                .basesUnderAttack(false)
                .healthyMutas(9)
                .hpLossFraction(0)
                .strikeDefended(false)
                .flockDefense(0)
                .tolerance(AirHarassEvaluator.tolerance(9));
    }

    @Test
    void aMutaIsHealthyFromSixtyPercentOfItsHitPoints() {
        int max = UnitType.Zerg_Mutalisk.maxHitPoints();
        int threshold = (int) Math.ceil(AirHarassEvaluator.HEALTHY_HP_FRACTION * max);

        assertTrue(AirHarassEvaluator.isHealthy(threshold, max));
        assertFalse(AirHarassEvaluator.isHealthy(threshold - 1, max));
        assertFalse(AirHarassEvaluator.isHealthy(0, 0));
    }

    @Test
    void theToleranceRatioGrowsWithHealthyMutasAndIsCapped() {
        assertEquals(0, AirHarassEvaluator.toleranceRatio(0));
        assertEquals(0, AirHarassEvaluator.toleranceRatio(AirHarassEvaluator.TOLERANCE_FREE_MUTAS));
        assertEquals(AirHarassEvaluator.TOLERANCE_RATIO_PER_MUTA,
                AirHarassEvaluator.toleranceRatio(AirHarassEvaluator.TOLERANCE_FREE_MUTAS + 1), 1e-9);
        assertEquals(AirHarassEvaluator.MAX_TOLERANCE_RATIO, AirHarassEvaluator.toleranceRatio(40), 1e-9);
    }

    @Test
    void theToleranceRisesWithEveryHealthyMuta() {
        for (int healthy = AirHarassEvaluator.TOLERANCE_FREE_MUTAS + 1; healthy < 20; healthy++) {
            assertTrue(AirHarassEvaluator.tolerance(healthy + 1) > AirHarassEvaluator.tolerance(healthy));
        }
    }

    @Test
    void aSmallFlockAvoidsAGoliathThatALargeFlockTakesOn() {
        double goliath = UnitStrength.antiAirStrength(UnitType.Terran_Goliath);

        assertTrue(AirHarassEvaluator.tolerance(AirHarassEvaluator.MIN_HEALTHY_MUTAS) < goliath);
        assertTrue(AirHarassEvaluator.tolerance(12) >= goliath);
    }

    @Test
    void aMissileTurretNeedsMoreHealthyMutasThanAGoliath() {
        double turret = UnitStrength.antiAirStrength(UnitType.Terran_Missile_Turret);
        double goliath = UnitStrength.antiAirStrength(UnitType.Terran_Goliath);

        assertTrue(turret > goliath);
        assertTrue(AirHarassEvaluator.tolerance(9) < turret);
        assertTrue(AirHarassEvaluator.tolerance(14) >= turret);
    }

    @Test
    void aFlockOfMutalisksEntersAHeatedTolerableBase() {
        assertEquals(EntryVerdict.ENTER, AirHarassEvaluator.entryVerdict(entry().build()));
    }

    @Test
    void harassPlaysOnlyAgainstTerranAndProtoss() {
        assertEquals(EntryVerdict.ENTER,
                AirHarassEvaluator.entryVerdict(entry().opponentRace(Race.Protoss).build()));
        assertEquals(EntryVerdict.WRONG_MATCHUP,
                AirHarassEvaluator.entryVerdict(entry().opponentRace(Race.Zerg).build()));
        assertEquals(EntryVerdict.WRONG_MATCHUP,
                AirHarassEvaluator.entryVerdict(entry().opponentRace(Race.Unknown).build()));
    }

    @Test
    void aSquadWithOtherAirCombatUnitsDoesNotHarass() {
        Map<UnitType, Integer> mixed = mutas(6);
        mixed.put(UnitType.Zerg_Scourge, 2);
        Map<UnitType, Integer> escorted = mutas(6);
        escorted.put(UnitType.Zerg_Overlord, 1);

        assertEquals(EntryVerdict.NOT_MUTALISKS, AirHarassEvaluator.entryVerdict(entry().composition(mixed).build()));
        assertEquals(EntryVerdict.NOT_MUTALISKS,
                AirHarassEvaluator.entryVerdict(entry().composition(new HashMap<>()).build()));
        assertEquals(EntryVerdict.ENTER, AirHarassEvaluator.entryVerdict(entry().composition(escorted).build()));
    }

    @Test
    void tooFewHealthyMutasDoNotHarass() {
        assertEquals(EntryVerdict.TOO_FEW, AirHarassEvaluator.entryVerdict(entry()
                .healthyMutas(AirHarassEvaluator.MIN_HEALTHY_MUTAS - 1).build()));
        assertEquals(EntryVerdict.ENTER, AirHarassEvaluator.entryVerdict(entry()
                .healthyMutas(AirHarassEvaluator.MIN_HEALTHY_MUTAS).build()));
    }

    @Test
    void aBaseUnderAttackKeepsTheFlockHome() {
        assertEquals(EntryVerdict.BASE_UNDER_ATTACK,
                AirHarassEvaluator.entryVerdict(entry().basesUnderAttack(true).build()));
    }

    @Test
    void noHeatedBaseIsNoTargetAndHeatedButDefendedBasesAreDefended() {
        List<AirHarassEvaluator.BaseOption<?>> cold = Collections.singletonList(
                new AirHarassEvaluator.BaseOption<>("main", null, 0, false, -1));
        List<AirHarassEvaluator.BaseOption<?>> defended = Collections.singletonList(
                new AirHarassEvaluator.BaseOption<>("main", null, 0, true, -1));

        assertEquals(EntryVerdict.NO_TARGET, AirHarassEvaluator.entryVerdict(entry().options(cold).build()));
        assertEquals(EntryVerdict.NO_TARGET,
                AirHarassEvaluator.entryVerdict(entry().options(Collections.emptyList()).build()));
        assertEquals(EntryVerdict.DEFENDED, AirHarassEvaluator.entryVerdict(entry().options(defended).build()));
    }

    @Test
    void theHottestTolerableBaseIsChosen() {
        AirHarassEvaluator.BaseOption<String> main = option("main", STRIKE, 90, -1);
        AirHarassEvaluator.BaseOption<String> natural = option("natural", new Position(1100, 3700), 120, -1);
        AirHarassEvaluator.BaseOption<String> defended = new AirHarassEvaluator.BaseOption<>("third", null, 0, true,
                -1);

        assertSame(natural, AirHarassEvaluator.chooseBase(Arrays.asList(main, natural, defended)));
        assertNull(AirHarassEvaluator.chooseBase(Collections.singletonList(defended)));
    }

    @Test
    void theBaseAwayFromTheContainOutscoresTheOneBehindIt() {
        AirHarassEvaluator.BaseOption<String> behindContain = option("natural", new Position(1100, 3700), 100, 200);
        AirHarassEvaluator.BaseOption<String> farSide = option("main", STRIKE, 80,
                AirHarassEvaluator.CONTAIN_AWAY_SCALE);

        assertSame(farSide, AirHarassEvaluator.chooseBase(Arrays.asList(behindContain, farSide)));
    }

    @Test
    void theContainTermOnlyRaisesAScoreAndStopsAtItsScale() {
        assertEquals(50, AirHarassEvaluator.baseScore(50, -1), 1e-9);
        assertEquals(50, AirHarassEvaluator.baseScore(50, 0), 1e-9);
        assertEquals(50 * (1 + AirHarassEvaluator.CONTAIN_AWAY_WEIGHT),
                AirHarassEvaluator.baseScore(50, AirHarassEvaluator.CONTAIN_AWAY_SCALE), 1e-9);
        assertEquals(AirHarassEvaluator.baseScore(50, AirHarassEvaluator.CONTAIN_AWAY_SCALE),
                AirHarassEvaluator.baseScore(50, 3 * AirHarassEvaluator.CONTAIN_AWAY_SCALE), 1e-9);
    }

    @Test
    void aHealthyFlockAtAnUndefendedBaseKeepsHarassing() {
        assertNull(AirHarassEvaluator.exitReason(exit().build()));
    }

    @Test
    void exitReasonsAreReadInOrder() {
        assertEquals(ExitReason.BASE_UNDER_ATTACK, AirHarassEvaluator.exitReason(exit()
                .basesUnderAttack(true).healthyMutas(0).hpLossFraction(1).strikeDefended(true).build()));
        assertEquals(ExitReason.TOO_FEW, AirHarassEvaluator.exitReason(exit()
                .healthyMutas(AirHarassEvaluator.EXIT_HEALTHY_MUTAS - 1).hpLossFraction(1).build()));
        assertEquals(ExitReason.HP_LOSS, AirHarassEvaluator.exitReason(exit()
                .hpLossFraction(AirHarassEvaluator.HP_LOSS_EXIT_FRACTION + 0.01).strikeDefended(true).build()));
        assertEquals(ExitReason.STRIKE_DEFENDED, AirHarassEvaluator.exitReason(exit().strikeDefended(true).build()));
    }

    @Test
    void theFlockLeavesWhenItStandsInMoreAntiAirThanItTolerates() {
        double tolerance = AirHarassEvaluator.tolerance(9);

        assertNull(AirHarassEvaluator.exitReason(exit().flockDefense(tolerance).build()));
        assertEquals(ExitReason.FLOCK_DEFENDED,
                AirHarassEvaluator.exitReason(exit().flockDefense(tolerance + 1).build()));
    }

    @Test
    void theHpLossExitIsStrictlyAboveItsFraction() {
        assertNull(AirHarassEvaluator.exitReason(exit()
                .hpLossFraction(AirHarassEvaluator.HP_LOSS_EXIT_FRACTION).build()));
        assertNull(AirHarassEvaluator.exitReason(exit()
                .healthyMutas(AirHarassEvaluator.EXIT_HEALTHY_MUTAS).build()));
    }

    @Test
    void hpLossCountsDeadMutasAndIgnoresRegeneration() {
        assertEquals(0.25, AirHarassEvaluator.hpLossFraction(1080, 810), 1e-9);
        assertEquals(0, AirHarassEvaluator.hpLossFraction(1080, 1100), 1e-9);
        assertEquals(0, AirHarassEvaluator.hpLossFraction(0, 0), 1e-9);
    }

    @Test
    void theSquadRetargetsOnlyOnceItHasSatAtTheBaseWithNothingToHit() {
        int arrivedProgress = NOW - AirHarassEvaluator.NO_TARGET_FRAMES;

        assertTrue(AirHarassEvaluator.shouldRetarget(true, true, false, NOW, NOW));
        assertTrue(AirHarassEvaluator.shouldRetarget(false, false, false, NOW, NOW));
        assertTrue(AirHarassEvaluator.shouldRetarget(false, true, true, NOW, arrivedProgress));
        assertFalse(AirHarassEvaluator.shouldRetarget(false, true, true, NOW, arrivedProgress + 1));
        assertFalse(AirHarassEvaluator.shouldRetarget(false, true, false, NOW, arrivedProgress));
    }

    @Test
    void theFlockArrivesWithinTheArriveRadius() {
        Position at = new Position(STRIKE.getX() + AirHarassEvaluator.ARRIVE_RADIUS, STRIKE.getY());
        Position short1 = new Position(STRIKE.getX() + AirHarassEvaluator.ARRIVE_RADIUS + 1, STRIKE.getY());

        assertTrue(AirHarassEvaluator.arrived(at, STRIKE));
        assertFalse(AirHarassEvaluator.arrived(short1, STRIKE));
        assertFalse(AirHarassEvaluator.arrived(null, STRIKE));
    }

    @Test
    void theEntryCheckRunsOnTheTickOutsideEveryLock() {
        int tick = AirHarassEvaluator.HARASS_TICK * 1000;

        assertTrue(AirHarassEvaluator.entryCheckDue(true, false, false, tick));
        assertFalse(AirHarassEvaluator.entryCheckDue(true, false, false, tick + 1));
        assertFalse(AirHarassEvaluator.entryCheckDue(false, false, false, tick));
        assertFalse(AirHarassEvaluator.entryCheckDue(true, true, false, tick));
        assertFalse(AirHarassEvaluator.entryCheckDue(true, false, true, tick));
    }

    @Test
    void aSquadThatLeftAHarassHoldsOnABlindAdvanceForTheHoldWindow() {
        int exited = NOW - AirHarassEvaluator.REENTRY_HOLD_FRAMES;

        assertTrue(AirHarassEvaluator.holdsBlindAdvance(exited, NOW, false, SquadStatus.RETREAT));
        assertTrue(AirHarassEvaluator.holdsBlindAdvance(exited, NOW, false, SquadStatus.RALLY));
        assertFalse(AirHarassEvaluator.holdsBlindAdvance(exited - 1, NOW, false, SquadStatus.RETREAT));
    }

    @Test
    void aHarassSquadRemovedEmptyClosesItsHarassAsWipedOut() {
        assertEquals(ExitReason.WIPED_OUT, AirHarassEvaluator.removalExit(SquadStatus.HARASS, 0));
        assertNull(AirHarassEvaluator.removalExit(SquadStatus.HARASS, 1));
        assertNull(AirHarassEvaluator.removalExit(SquadStatus.RETREAT, 0));
        assertNull(AirHarassEvaluator.removalExit(SquadStatus.RUNBY, 0));
    }

    @Test
    void aKillBesideAnotherSquadIsCreditedOnlyWhenAHarassingMutaWasAttackingIt() {
        assertTrue(AirHarassEvaluator.creditsKill(true, false, false));
        assertTrue(AirHarassEvaluator.creditsKill(true, true, true));
        assertTrue(AirHarassEvaluator.creditsKill(false, true, false));
        assertFalse(AirHarassEvaluator.creditsKill(false, true, true));
        assertFalse(AirHarassEvaluator.creditsKill(false, false, false));
    }

    @Test
    void aMeasuredAdvanceOrASquadThatNeverHarassedIsNotHeld() {
        assertFalse(AirHarassEvaluator.holdsBlindAdvance(NOW - 10, NOW, true, SquadStatus.RETREAT));
        assertFalse(AirHarassEvaluator.holdsBlindAdvance(0, NOW, false, SquadStatus.RETREAT));
        assertFalse(AirHarassEvaluator.holdsBlindAdvance(NOW - 10, NOW, false, SquadStatus.FIGHT));
    }

    @Test
    void anExposedTargetEntersWhenNoBaseHasAToleratedStrikePoint() {
        List<AirHarassEvaluator.BaseOption<?>> defended = Collections.singletonList(
                new AirHarassEvaluator.BaseOption<>("main", null, 0, true, -1));
        List<AirHarassEvaluator.BaseOption<?>> none = Collections.emptyList();

        assertEquals(EntryVerdict.ENTER,
                AirHarassEvaluator.entryVerdict(entry().options(defended).exposedTarget(true).build()));
        assertEquals(EntryVerdict.ENTER,
                AirHarassEvaluator.entryVerdict(entry().options(none).exposedTarget(true).build()));
        assertEquals(EntryVerdict.DEFENDED,
                AirHarassEvaluator.entryVerdict(entry().options(defended).exposedTarget(false).build()));
        assertEquals(EntryVerdict.NO_TARGET,
                AirHarassEvaluator.entryVerdict(entry().options(none).exposedTarget(false).build()));
        assertEquals(EntryVerdict.BASE_UNDER_ATTACK, AirHarassEvaluator.entryVerdict(
                entry().options(none).exposedTarget(true).basesUnderAttack(true).build()));
        assertEquals(EntryVerdict.TOO_FEW, AirHarassEvaluator.entryVerdict(
                entry().options(none).exposedTarget(true).healthyMutas(AirHarassEvaluator.MIN_HEALTHY_MUTAS - 1)
                        .build()));
    }

    @Test
    void aFlockStandingInMoreAntiAirThanItToleratesDoesNotEnterAnExposedTargetEither() {
        List<AirHarassEvaluator.BaseOption<?>> none = Collections.emptyList();

        assertEquals(EntryVerdict.DEFENDED, AirHarassEvaluator.entryVerdict(
                entry().options(none).exposedTarget(true).flockDefense(3.0).tolerance(2.0).build()));
        assertEquals(EntryVerdict.ENTER, AirHarassEvaluator.entryVerdict(
                entry().options(none).exposedTarget(true).flockDefense(2.0).tolerance(2.0).build()));
    }

    @Test
    void onlyAMeasuredEngageBreaksTheRetreatLockAHarassExitArmed() {
        assertTrue(AirHarassEvaluator.breaksExitLock(true, CombatSimulator.CombatResult.ENGAGE, true));
        assertFalse(AirHarassEvaluator.breaksExitLock(true, CombatSimulator.CombatResult.ENGAGE, false));
        assertFalse(AirHarassEvaluator.breaksExitLock(true, CombatSimulator.CombatResult.ADVANCE, true));
        assertFalse(AirHarassEvaluator.breaksExitLock(true, CombatSimulator.CombatResult.RETREAT, true));
        assertFalse(AirHarassEvaluator.breaksExitLock(false, CombatSimulator.CombatResult.ENGAGE, true));
    }

    @Test
    void aMeasuredEngageTheFrameAfterAHarassExitActs() {
        Squad squad = new AirSquad();
        squad.setHarassExitFrame(NOW);
        squad.startRetreatLock(NOW);

        assertTrue(squad.isRetreatLocked(NOW + 1));
        assertTrue(AirHarassEvaluator.breaksExitLock(squad.isHarassExitLocked(NOW + 1),
                CombatSimulator.CombatResult.ENGAGE, true));
        assertFalse(AirHarassEvaluator.holdsBlindAdvance(squad.getHarassExitFrame(), NOW + 1, true,
                SquadStatus.RETREAT));
        squad.clearRetreatLock();
        assertFalse(squad.isRetreatLocked(NOW + 1));
    }

    @Test
    void harassEntryStaysClosedForTheReentryHoldAfterABrokenExitLock() {
        int broke = NOW + 1;

        assertFalse(AirHarassEvaluator.holdsReentry(0, NOW));
        assertTrue(AirHarassEvaluator.holdsReentry(broke, broke));
        assertTrue(AirHarassEvaluator.holdsReentry(broke, broke + AirHarassEvaluator.REENTRY_HOLD_FRAMES));
        assertFalse(AirHarassEvaluator.holdsReentry(broke, broke + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 1));
    }

    @Test
    void anExitEngageBreakNoLongerFeedsAnImmediateReEntry() {
        Squad squad = new AirSquad();
        squad.setHarassExitFrame(NOW);
        squad.startRetreatLock(NOW);
        int broke = NOW + 1;
        assertTrue(AirHarassEvaluator.breaksExitLock(squad.isHarassExitLocked(broke),
                CombatSimulator.CombatResult.ENGAGE, true));
        squad.clearRetreatLock();
        squad.setHarassExitEngageFrame(broke);
        int reentry = NOW + 24;

        assertTrue(AirHarassEvaluator.entryCheckDue(true, squad.isRetreatLocked(reentry),
                squad.isFightLocked(reentry), reentry));
        assertTrue(AirHarassEvaluator.holdsReentry(squad.getHarassExitEngageFrame(), reentry));
        int reopened = NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES + AirHarassEvaluator.HARASS_TICK;
        assertTrue(AirHarassEvaluator.entryCheckDue(true, false, false, reopened));
        assertFalse(AirHarassEvaluator.holdsReentry(squad.getHarassExitEngageFrame(), reopened));
    }

    @Test
    void theReentryHoldClosesOnlyTheTargetTheFailedHarassWasOn() {
        Position baseA = new Position(3808, 2096);
        Position starport = new Position(3000, 1000);
        int broke = NOW + 1;
        int inside = broke + AirHarassEvaluator.REENTRY_HOLD_FRAMES - 1;

        assertEquals(AirHarassEvaluator.ReentryHold.TARGET,
                AirHarassEvaluator.reentryHold(broke, baseA, false, inside));
        assertEquals(AirHarassEvaluator.ReentryHold.ALL, AirHarassEvaluator.reentryHold(broke, null, false, inside));
        assertEquals(AirHarassEvaluator.ReentryHold.NONE,
                AirHarassEvaluator.reentryHold(broke, baseA, false,
                        broke + AirHarassEvaluator.REENTRY_HOLD_FRAMES + 1));
        assertEquals(AirHarassEvaluator.ReentryHold.NONE, AirHarassEvaluator.reentryHold(0, baseA, false, inside));
        assertTrue(AirHarassEvaluator.isFailedTarget(baseA, baseA));
        assertTrue(AirHarassEvaluator.isFailedTarget(baseA, new Position(3808 + 100, 2096)));
        assertFalse(AirHarassEvaluator.isFailedTarget(baseA, starport));
        assertFalse(AirHarassEvaluator.isFailedTarget(null, baseA));
    }

    @Test
    void anExposedStarportElsewhereIsSelectableInsideTheHoldWindowAndTheFailedBaseIsNot() {
        Position baseA = new Position(3808, 2096);
        AirHarassTargeting.Contact starport = new AirHarassTargeting.Contact(9, UnitType.Terran_Starport,
                new Position(3000, 1000), UnitType.Terran_Starport.maxHitPoints(), 1.0);
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Collections.singletonList(starport), 6,
                Collections.emptyList());
        int inside = NOW + AirHarassEvaluator.REENTRY_HOLD_FRAMES - 1;

        assertEquals(AirHarassEvaluator.ReentryHold.TARGET,
                AirHarassEvaluator.reentryHold(NOW, baseA, false, inside));
        assertFalse(AirHarassEvaluator.isFailedTarget(baseA, groups.get(0).getAnchor()));
        ExposedTargets.Group chosen = ExposedTargets.choose(groups, Collections.emptyList(), 0, new Position(0, 0));
        assertEquals(groups.get(0), chosen);
        assertTrue(AirHarassEvaluator.exposedOutscoresBase(ExposedTargets.score(chosen, new Position(0, 0)), -1));
        assertTrue(AirHarassEvaluator.isFailedTarget(baseA, baseA));
    }

    @Test
    void anExposedGroupIsScoredAgainstTheBestBaseInHeatUnits() {
        double groupScore = 3.0;
        double weakBase = AirHarassEvaluator.baseScore(60, -1);
        double hotBase = AirHarassEvaluator.baseScore(400, -1);

        assertTrue(AirHarassEvaluator.exposedOutscoresBase(groupScore, weakBase));
        assertFalse(AirHarassEvaluator.exposedOutscoresBase(groupScore, hotBase));
        assertTrue(AirHarassEvaluator.exposedOutscoresBase(0.1, -1));
    }

    @Test
    void anExitWithoutABreakLeavesHarassEntryOpen() {
        Squad squad = new AirSquad();
        squad.setHarassExitFrame(NOW);
        squad.startRetreatLock(NOW);
        int afterLock = NOW + 48;

        assertFalse(squad.isRetreatLocked(afterLock));
        assertTrue(AirHarassEvaluator.entryCheckDue(true, false, false, afterLock));
        assertFalse(AirHarassEvaluator.holdsReentry(squad.getHarassExitEngageFrame(), afterLock));
    }

    @Test
    void theExitLockIsOnlyTheLockTheExitArmed() {
        Squad squad = new AirSquad();
        squad.setHarassExitFrame(NOW);
        squad.startRetreatLock(NOW);
        int lockEnd = NOW + 36;

        assertTrue(squad.isHarassExitLocked(lockEnd - 1));
        assertFalse(squad.isHarassExitLocked(lockEnd));
        squad.startRetreatLock(NOW + 10);
        assertTrue(squad.isRetreatLocked(NOW + 11));
        assertFalse(squad.isHarassExitLocked(NOW + 11));

        Squad neverHarassed = new AirSquad();
        neverHarassed.startRetreatLock(NOW);
        assertFalse(neverHarassed.isHarassExitLocked(NOW + 1));
    }
}
