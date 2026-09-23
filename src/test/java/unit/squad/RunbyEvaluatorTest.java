package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.UnitStrength;
import util.StaticDefenseZone;
import util.TravelTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.RunbyEvaluator.EntryVerdict;

class RunbyEvaluatorTest {

    private static final Position ARC = new Position(3440, 1064);
    private static final Position MINERAL_LINE = new Position(3600, 400);
    private static final Position OUR_SIDE = new Position(600, 2400);
    private static final CombatSimulator.CombatResult NO_SIM = null;

    private static RunbyEvaluator.ArmyUnit zealot(Position position, boolean fresh, boolean cleared) {
        return new RunbyEvaluator.ArmyUnit(UnitType.Protoss_Zealot, position, fresh, cleared);
    }

    private static List<RunbyEvaluator.ArmyUnit> armyAtOurBase(int zealots) {
        List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        for (int i = 0; i < zealots; i++) {
            army.add(zealot(OUR_SIDE, true, false));
        }
        return army;
    }

    private static RunbyEvaluator.EntryInput.EntryInputBuilder passing() {
        return RunbyEvaluator.EntryInput.builder()
                .zerglingsOnly(true)
                .metabolicBoost(true)
                .size(8)
                .squadCenter(ARC)
                .anchor(MINERAL_LINE)
                .army(armyAtOurBase(10))
                .zones(Collections.emptyList());
    }

    private static List<RunbyEvaluator.ArmyUnit> with(List<RunbyEvaluator.ArmyUnit> army,
                                                      RunbyEvaluator.ArmyUnit... more) {
        List<RunbyEvaluator.ArmyUnit> all = new ArrayList<>(army);
        all.addAll(Arrays.asList(more));
        return all;
    }

    private static StaticDefenseZone cannonAt(Position position) {
        return new StaticDefenseZone(UnitType.Protoss_Photon_Cannon, position,
                UnitType.Protoss_Photon_Cannon.groundWeapon().maxRange());
    }

    @Test
    void entersOnlyWhenEveryGatePasses() {
        assertEquals(EntryVerdict.ENTER, RunbyEvaluator.entryVerdict(passing().build()));
    }

    @Test
    void refusesASquadThatIsNotAllZerglings() {
        assertEquals(EntryVerdict.NOT_ZERGLINGS, RunbyEvaluator.entryVerdict(passing().zerglingsOnly(false).build()));
    }

    @Test
    void refusesWithoutMetabolicBoost() {
        assertEquals(EntryVerdict.NO_SPEED, RunbyEvaluator.entryVerdict(passing().metabolicBoost(false).build()));
    }

    @Test
    void refusesBelowTheMinimumSize() {
        assertEquals(EntryVerdict.TOO_FEW,
                RunbyEvaluator.entryVerdict(passing().size(RunbyEvaluator.MIN_LINGS - 1).build()));
        assertEquals(EntryVerdict.ENTER, RunbyEvaluator.entryVerdict(passing().size(RunbyEvaluator.MIN_LINGS).build()));
    }

    @Test
    void refusesWithoutATargetBase() {
        assertEquals(EntryVerdict.NO_TARGET, RunbyEvaluator.entryVerdict(passing().anchor(null).build()));
    }

    @Test
    void refusesWhenAFreshArmyUnitIsNearTheTarget() {
        List<RunbyEvaluator.ArmyUnit> army = with(armyAtOurBase(10),
                zealot(new Position(3700, 600), true, false));

        assertEquals(EntryVerdict.ARMY_NEAR_TARGET, RunbyEvaluator.entryVerdict(passing().army(army).build()));
    }

    @Test
    void refusesWhenAFreshArmyUnitIsNearThePathToTheTarget() {
        Position farArc = new Position(3500, 3000);
        Position onThePath = new Position(3300, 2000);
        List<RunbyEvaluator.ArmyUnit> army = with(armyAtOurBase(10), zealot(onThePath, true, false));

        assertTrue(onThePath.getDistance(MINERAL_LINE) > RunbyEvaluator.ARMY_CLEARANCE);
        assertEquals(EntryVerdict.ARMY_NEAR_TARGET,
                RunbyEvaluator.entryVerdict(passing().squadCenter(farArc).army(army).build()));
    }

    @Test
    void aStaleUnclearedArmyPositionNearTheTargetBlocksEntry() {
        List<RunbyEvaluator.ArmyUnit> army = with(armyAtOurBase(10), zealot(new Position(3650, 450), false, false));

        assertEquals(EntryVerdict.ARMY_NEAR_TARGET, RunbyEvaluator.entryVerdict(passing().army(army).build()));
    }

    @Test
    void aStaleArmyPositionOurVisionHasClearedDoesNotBlockEntry() {
        List<RunbyEvaluator.ArmyUnit> army = with(armyAtOurBase(10), zealot(new Position(3650, 450), false, true));

        assertEquals(EntryVerdict.ENTER, RunbyEvaluator.entryVerdict(passing().army(army).build()));
    }

    @Test
    void aVisibleUnitWithAnOldObservationStampCountsAsFresh() {
        assertTrue(RunbyEvaluator.isFresh(true, 100, 9000));
        assertFalse(RunbyEvaluator.isFresh(false, 100, 9000));
    }

    @Test
    void anUnseenUnitStaysFreshForTheFreshWindowOnly() {
        assertTrue(RunbyEvaluator.isFresh(false, 9000 - RunbyEvaluator.FRESH_FRAMES, 9000));
        assertFalse(RunbyEvaluator.isFresh(false, 9000 - RunbyEvaluator.FRESH_FRAMES - 1, 9000));
    }

    @Test
    void refusesWhenTooLittleOfTheArmyIsFreshlySeenAway() {
        List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        army.add(zealot(OUR_SIDE, true, false));
        army.add(zealot(OUR_SIDE, false, false));
        army.add(zealot(OUR_SIDE, false, false));

        assertEquals(EntryVerdict.NO_ARMY_EVIDENCE, RunbyEvaluator.entryVerdict(passing().army(army).build()));
    }

    @Test
    void halfTheArmyFreshlySeenAwayIsEnoughEvidence() {
        List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        army.add(zealot(OUR_SIDE, true, false));
        army.add(zealot(OUR_SIDE, false, false));

        assertEquals(EntryVerdict.ENTER, RunbyEvaluator.entryVerdict(passing().army(army).build()));
    }

    @Test
    void noTrackedArmyIsNoEvidence() {
        assertEquals(EntryVerdict.NO_ARMY_EVIDENCE,
                RunbyEvaluator.entryVerdict(passing().army(Collections.emptyList()).build()));
    }

    @Test
    void refusesWhenStaticDefenceCoversTheTarget() {
        List<StaticDefenseZone> zones = Collections.singletonList(cannonAt(new Position(3650, 400)));

        assertEquals(EntryVerdict.STATIC_DEFENSE, RunbyEvaluator.entryVerdict(passing().zones(zones).build()));
    }

    @Test
    void staticDefenceElsewhereDoesNotRefuse() {
        List<StaticDefenseZone> zones = Collections.singletonList(cannonAt(new Position(2400, 1600)));

        assertEquals(EntryVerdict.ENTER, RunbyEvaluator.entryVerdict(passing().zones(zones).build()));
    }

    @Test
    void theTallyCountsFreshArmyNearTheTargetAndCoveringStaticDefence() {
        List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        army.add(zealot(new Position(3650, 450), true, false));
        army.add(zealot(new Position(3550, 350), true, false));
        army.add(zealot(new Position(3650, 450), false, false));
        army.add(zealot(OUR_SIDE, true, false));
        List<StaticDefenseZone> zones = Arrays.asList(cannonAt(new Position(3650, 400)),
                cannonAt(new Position(2400, 1600)));

        double expected = 2 * UnitStrength.groundToGround(UnitType.Protoss_Zealot)
                + UnitStrength.groundToGround(UnitType.Protoss_Photon_Cannon);
        assertEquals(expected, RunbyEvaluator.enemyTally(army, zones, MINERAL_LINE), 1e-9);
    }

    @Test
    void theTallyAbortsInsideTheWindow() {
        double ours = RunbyEvaluator.ourTally(8);
        double overwhelming = RunbyEvaluator.ABORT_RATIO * ours;

        assertTrue(RunbyEvaluator.shouldAbort(true, overwhelming, ours, false, NO_SIM, false, 0, 0));
        assertFalse(RunbyEvaluator.shouldAbort(true, overwhelming * 0.99, ours, false, NO_SIM, false, 0, 0));
    }

    @Test
    void theTallyNeverAbortsAfterTheWindow() {
        double ours = RunbyEvaluator.ourTally(8);

        assertFalse(RunbyEvaluator.shouldAbort(false, 100 * ours, ours, true, CombatSimulator.CombatResult.RETREAT,
                true, 0.01, 1.0));
    }

    @Test
    void theWindowLastsUntilTheGraceAfterArrivalAndNeverReopens() {
        int arrived = 7000;

        assertTrue(RunbyEvaluator.abortWindowOpen(false, -1, 9000));
        assertTrue(RunbyEvaluator.abortWindowOpen(false, arrived, arrived + RunbyEvaluator.ABORT_GRACE_AFTER_ARRIVAL));
        assertFalse(RunbyEvaluator.abortWindowOpen(false, arrived,
                arrived + RunbyEvaluator.ABORT_GRACE_AFTER_ARRIVAL + 1));
        assertFalse(RunbyEvaluator.abortWindowOpen(true, -1, 9000));
    }

    @Test
    void insideTheBaseAMeasuredSimRetreatFarBelowThresholdAborts() {
        double ours = RunbyEvaluator.ourTally(8);
        CombatSimulator.CombatResult retreat = CombatSimulator.CombatResult.RETREAT;

        assertTrue(RunbyEvaluator.shouldAbort(true, 0, ours, true, retreat, true, 0.49, 1.0));
        assertFalse(RunbyEvaluator.shouldAbort(true, 0, ours, true, retreat, true, 0.5, 1.0));
        assertFalse(RunbyEvaluator.shouldAbort(true, 0, ours, true, retreat, false, 0.1, 1.0));
        assertFalse(RunbyEvaluator.shouldAbort(true, 0, ours, false, retreat, true, 0.1, 1.0));
        assertFalse(RunbyEvaluator.shouldAbort(true, 0, ours, true, CombatSimulator.CombatResult.ENGAGE, true, 0.1,
                1.0));
    }

    @Test
    void penetrateEndsAtTheBudget() {
        assertFalse(RunbyEvaluator.penetrateEnds(1000 + 599, 1000, 600, false, false));
        assertTrue(RunbyEvaluator.penetrateEnds(1000 + 600, 1000, 600, false, false));
    }

    @Test
    void penetrateEndsInsideTheBaseWithASafeWorkerInReach() {
        assertTrue(RunbyEvaluator.penetrateEnds(1010, 1000, 600, true, true));
        assertFalse(RunbyEvaluator.penetrateEnds(1010, 1000, 600, true, false));
        assertFalse(RunbyEvaluator.penetrateEnds(1010, 1000, 600, false, true));
    }

    @Test
    void theBudgetIsTheUnupgradedWalkWithMargin() {
        int expected = (int) (TravelTime.framesToReach(800, UnitType.Zerg_Zergling.topSpeed())
                * RunbyEvaluator.PENETRATE_BUDGET_FACTOR);

        assertEquals(expected, RunbyEvaluator.penetrateBudget(800));
        assertTrue(RunbyEvaluator.penetrateBudget(1600) > RunbyEvaluator.penetrateBudget(800));
    }

    @Test
    void theBaseHasNothingLeftAfterTheNoTargetWindow() {
        assertFalse(RunbyEvaluator.noTargets(1000 + RunbyEvaluator.NO_TARGET_FRAMES - 1, 1000));
        assertTrue(RunbyEvaluator.noTargets(1000 + RunbyEvaluator.NO_TARGET_FRAMES, 1000));
    }

    @Test
    void decisionsRunOnTheirOwnTick() {
        assertFalse(RunbyEvaluator.decisionTickDue(1011, 1000));
        assertTrue(RunbyEvaluator.decisionTickDue(1012, 1000));
        assertTrue(RunbyEvaluator.winnableRefreshDue(1000, -1));
        assertFalse(RunbyEvaluator.winnableRefreshDue(1023, 1000));
        assertTrue(RunbyEvaluator.winnableRefreshDue(1024, 1000));
    }

    @Test
    void distanceToSegmentClampsToTheEnds() {
        Position start = new Position(0, 0);
        Position end = new Position(100, 0);

        assertEquals(10, RunbyEvaluator.distanceToSegment(new Position(50, 10), start, end), 1e-9);
        assertEquals(50, RunbyEvaluator.distanceToSegment(new Position(-50, 0), start, end), 1e-9);
        assertEquals(5, RunbyEvaluator.distanceToSegment(new Position(3, 4), start, start), 1e-9);
    }
}
