package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import bwapi.WalkPosition;
import info.tracking.EnemyReachMemory;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentCollapseTest {

    private static final Position CHOKE = new Position(1600, 1600);
    private static final Position FACE_TARGET = new Position(1600, 960);
    private static final int ARC_RADIUS = 384;
    private static final int ARC_DEGREES = 90;
    private static final int MEMBERS = 12;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final int PADDING = SquadManager.containmentDefensePadding(
            Collections.singletonList(UnitType.Zerg_Zergling));
    private static final double TERRAN_THRESHOLD = 1.44;
    private static final DoubleSupplier FAVOURABLE = () -> 2.0;
    private static final DoubleSupplier UNFAVOURABLE = () -> 1.2;

    private static Arc heldArc() {
        Arc arc = new Arc(CHOKE, FACE_TARGET, ARC_RADIUS, ARC_DEGREES, MEMBERS);
        arc.compute(ALL_WALKABLE, Collections.emptyList(), PADDING, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static List<Position> inSector(Arc arc, Position... enemies) {
        List<Position> kept = new ArrayList<>();
        for (Position enemy : enemies) {
            if (ContainmentCollapse.inSector(arc.getCenter(), arc.getPositions(), enemy)) {
                kept.add(enemy);
            }
        }
        return kept;
    }

    private static StaticDefenseZone bunkerAt(Position position) {
        return new StaticDefenseZone(UnitType.Terran_Bunker, position,
                EnemyReachMemory.baseGroundRange(UnitType.Terran_Bunker));
    }

    private static Position[] marinesInTheBowl() {
        return new Position[] {new Position(1580, 1420), new Position(1620, 1400), new Position(1600, 1450)};
    }

    @Test
    void collapseFiresWithArmedEnemiesInTheSectorAndAFavourableSim() {
        Arc arc = heldArc();
        List<Position> armed = inSector(arc, marinesInTheBowl());

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(3, armed.size());
        assertEquals(ContainmentCollapse.Outcome.COLLAPSE, read.getOutcome());
        assertEquals(3, read.getEnemiesInSector());
        assertEquals(2.0, read.getRatio());
        assertTrue(read.isStaticClear());
        assertEquals(8, read.getFlanks());
        assertEquals(SquadManager.ContainmentVerdict.COLLAPSE,
                SquadManager.rankCollapse(false, true, SquadManager.ContainmentVerdict.RETREAT));
    }

    @Test
    void collapseDoesNotFireWhenTheEnemyCentroidIsInsideBunkerReach() {
        Arc arc = heldArc();
        List<Position> armed = inSector(arc, marinesInTheBowl());
        Position centroid = ContainmentCollapse.centroid(armed);
        List<StaticDefenseZone> bunker = Collections.singletonList(bunkerAt(new Position(centroid.getX() + 96,
                centroid.getY())));

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, bunker, PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, MEMBERS);

        assertFalse(read.isStaticClear());
        assertEquals(ContainmentCollapse.Outcome.STATIC_COVERED, read.getOutcome());
        assertEquals(SquadManager.ContainmentVerdict.RETREAT,
                SquadManager.rankCollapse(false, false, SquadManager.ContainmentVerdict.RETREAT));
    }

    @Test
    void aBunkerOutOfReachOfTheCentroidDoesNotBlockTheCollapse() {
        Arc arc = heldArc();
        List<Position> armed = inSector(arc, marinesInTheBowl());
        List<StaticDefenseZone> bunker = Collections.singletonList(bunkerAt(new Position(1600, 2240)));

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, bunker, PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.COLLAPSE, read.getOutcome());
    }

    @Test
    void anUnfavourableSectorSimRejectsTheCollapse() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING,
                UNFAVOURABLE, TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.SIM_UNFAVOURABLE, read.getOutcome());
        assertEquals(1.2, read.getRatio());
    }

    @Test
    void aSimAtExactlyTheThresholdCollapses() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING,
                () -> TERRAN_THRESHOLD, TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.COLLAPSE, read.getOutcome());
    }

    @Test
    void tooFewArmedEnemiesRejectWithoutRunningTheSim() {
        List<Position> armed = inSector(heldArc(), new Position(1580, 1420), new Position(1620, 1400));
        AtomicInteger simRuns = new AtomicInteger();

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING, () -> {
            simRuns.incrementAndGet();
            return 5.0;
        }, TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.TOO_FEW_ENEMIES, read.getOutcome());
        assertEquals(ContainmentCollapse.NOT_SIMULATED, read.getRatio());
        assertEquals(0, simRuns.get());
    }

    @Test
    void aSquadBelowTheMinimumSizeDoesNotCollapseAndSkipsTheSim() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());
        AtomicInteger simRuns = new AtomicInteger();
        DoubleSupplier counted = () -> {
            simRuns.incrementAndGet();
            return 4.81;
        };

        ContainmentCollapse.Read small = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING, counted,
                TERRAN_THRESHOLD, true, ContainmentCollapse.MIN_COLLAPSE_MEMBERS - 1);

        assertEquals(ContainmentCollapse.Outcome.TOO_FEW_MEMBERS, small.getOutcome());
        assertEquals(ContainmentCollapse.NOT_SIMULATED, small.getRatio());
        assertEquals(0, simRuns.get());
        assertEquals(ContainmentCollapse.Outcome.TOO_FEW_MEMBERS,
                ContainmentCollapse.evaluate(3, 2, true, 4.81, TERRAN_THRESHOLD, true),
                "the two-member squad that read 4.81 against 3 enemies");
    }

    @Test
    void aSquadAtTheMinimumSizeCollapsesWithFlanksOnBothSides() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, ContainmentCollapse.MIN_COLLAPSE_MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.COLLAPSE, read.getOutcome());
        assertTrue(read.getFlanks() >= 2, "a collapsing squad always has a flank on each side");
        assertTrue(ContainmentCollapse.MIN_COLLAPSE_MEMBERS >= ContainmentCollapse.MIN_ENEMIES_IN_SECTOR);
    }

    @Test
    void tooFewEnemiesStillRanksAboveTooFewMembers() {
        assertEquals(ContainmentCollapse.Outcome.TOO_FEW_ENEMIES,
                ContainmentCollapse.evaluate(2, 2, true, ContainmentCollapse.NOT_SIMULATED, TERRAN_THRESHOLD, true));
    }

    @Test
    void aSiegedTankOrLurkerCoversTheCollapseButMobileUnitsAndHurtMarksDoNot() {
        StaticDefenseZone bunker = bunkerAt(new Position(3000, 3000));
        StaticDefenseZone sieged = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
                new Position(1600, 1300), 384);
        StaticDefenseZone lurker = new StaticDefenseZone(UnitType.Zerg_Lurker, new Position(1200, 1200), 192);
        StaticDefenseZone marine = new StaticDefenseZone(UnitType.Terran_Marine, new Position(1600, 1420), 128);
        StaticDefenseZone hurt = new StaticDefenseZone(UnitType.None, new Position(1600, 1420), 64);

        List<StaticDefenseZone> kept = ContainmentCollapse.fixedFireZones(Arrays.asList(bunker, sieged, lurker,
                marine, hurt));

        assertEquals(Arrays.asList(bunker, sieged, lurker), kept);
    }

    @Test
    void aCollapseUnderASiegedTankIsStaticCovered() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());
        StaticDefenseZone sieged = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
                new Position(1600, 1300), 384);

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed,
                ContainmentCollapse.fixedFireZones(Collections.singletonList(sieged)), PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.STATIC_COVERED, read.getOutcome());
        assertFalse(read.isStaticClear());
    }

    @Test
    void theMatchupGateExcludesOnlyProtoss() {
        assertFalse(ContainmentCollapse.appliesAgainst(Race.Protoss));
        assertTrue(ContainmentCollapse.appliesAgainst(Race.Terran));
        assertTrue(ContainmentCollapse.appliesAgainst(Race.Zerg));
        assertTrue(ContainmentCollapse.appliesAgainst(Race.Unknown));
    }

    @Test
    void noArmedEnemyInTheSectorReadsNothing() {
        assertNull(ContainmentCollapse.read(Collections.emptyList(), Collections.emptyList(), PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, true, MEMBERS));
    }

    @Test
    void aSquadThatCannotRenewItsFightLockDoesNotCollapseAgain() {
        List<Position> armed = inSector(heldArc(), marinesInTheBowl());

        ContainmentCollapse.Read read = ContainmentCollapse.read(armed, Collections.emptyList(), PADDING, FAVOURABLE,
                TERRAN_THRESHOLD, false, MEMBERS);

        assertEquals(ContainmentCollapse.Outcome.LOCK_REFUSED, read.getOutcome());
    }

    @Test
    void aBaseUnderAttackOutranksACollapse() {
        assertEquals(SquadManager.ContainmentVerdict.BREAK_ALL,
                SquadManager.rankCollapse(true, true, SquadManager.ContainmentVerdict.BREAK_ALL));
    }

    @Test
    void aCollapseOutranksAttritionPushBackAndTheThrottle() {
        SquadManager.ContainmentVerdict bleeding = SquadManager.containmentVerdict(false, true,
                SquadManager.OutrangedHit.NONE, true, false, false, false, true);
        SquadManager.ContainmentVerdict pushBack = SquadManager.containmentVerdict(false, false,
                SquadManager.OutrangedHit.ARC_KEPT, true, false, false, false, true);
        SquadManager.ContainmentVerdict throttled = SquadManager.containmentVerdict(false, false,
                SquadManager.OutrangedHit.NONE, true, true, false, false, true);

        assertEquals(SquadManager.ContainmentVerdict.RETREAT, bleeding);
        assertEquals(SquadManager.ContainmentVerdict.COLLAPSE, SquadManager.rankCollapse(false, true, bleeding));
        assertEquals(SquadManager.ContainmentVerdict.COLLAPSE, SquadManager.rankCollapse(false, true, pushBack));
        assertEquals(SquadManager.ContainmentVerdict.COLLAPSE, SquadManager.rankCollapse(false, true, throttled));
    }

    @Test
    void theSectorIsTheBowlOnOurSideOfTheChoke() {
        Arc arc = heldArc();

        assertTrue(ContainmentCollapse.inSector(CHOKE, arc.getPositions(), new Position(1600, 1300)));
        assertFalse(ContainmentCollapse.inSector(CHOKE, arc.getPositions(), new Position(1600, 1800)),
                "behind the choke, on the enemy's side");
        assertFalse(ContainmentCollapse.inSector(CHOKE, arc.getPositions(), new Position(1600, 1100)),
                "past the arc's reach");
        assertFalse(ContainmentCollapse.inSector(CHOKE, arc.getPositions(), new Position(1900, 1560)),
                "outside the arc's bearings");
    }

    @Test
    void anArcOfOnePointHasNoSector() {
        assertFalse(ContainmentCollapse.inSector(CHOKE, Collections.singletonList(new Position(1600, 1216)),
                new Position(1600, 1300)));
    }

    @Test
    void theOuterThirdOnEachSideFlanks() {
        Arc arc = heldArc();
        List<Position> members = new ArrayList<>(arc.getPositions());

        int[] sides = ContainmentCollapse.flankSides(CHOKE, arc.getMidpoint(), members);

        int left = 0;
        int right = 0;
        for (int side : sides) {
            left += side < 0 ? 1 : 0;
            right += side > 0 ? 1 : 0;
        }
        assertEquals(4, left);
        assertEquals(4, right);
        assertEquals(sides[0], -sides[members.size() - 1], "the two ends of the arc flank on opposite sides");
        assertTrue(sides[0] != 0);
        assertEquals(0, sides[members.size() / 2]);
    }

    @Test
    void aSquadOfTwoHasNoFlanks() {
        int[] sides = ContainmentCollapse.flankSides(CHOKE, new Position(1600, 1216),
                Arrays.asList(new Position(1500, 1250), new Position(1700, 1250)));

        assertEquals(0, ContainmentCollapse.flankCount(2));
        assertEquals(0, sides[0]);
        assertEquals(0, sides[1]);
    }

    @Test
    void aFlankWrapsPastTheEnemyCentroidOnItsOwnSide() {
        Arc arc = heldArc();
        Position centroid = new Position(1600, 1420);
        Position leftFlank = new Position(1330, 1330);
        Position rightFlank = new Position(1870, 1330);

        Position leftWrap = ContainmentCollapse.wrapPoint(CHOKE, centroid, arc.getMidpoint(), leftFlank);
        Position rightWrap = ContainmentCollapse.wrapPoint(CHOKE, centroid, arc.getMidpoint(), rightFlank);

        assertTrue(leftWrap.getDistance(CHOKE) < centroid.getDistance(CHOKE), "wrap lies between centroid and choke");
        assertTrue(rightWrap.getDistance(CHOKE) < centroid.getDistance(CHOKE));
        assertTrue(leftWrap.getX() < centroid.getX(), "left flank wraps on the left");
        assertTrue(rightWrap.getX() > centroid.getX(), "right flank wraps on the right");
        assertEquals(ContainmentCollapse.WRAP_DEPTH, leftWrap.getY() - centroid.getY(), 1);
    }

    @Test
    void aCentroidOnTheChokeWrapsAlongTheLineFromTheArc() {
        Position midpoint = new Position(1600, 1216);

        Position wrap = ContainmentCollapse.wrapPoint(CHOKE, CHOKE, midpoint, new Position(1400, 1600));

        assertTrue(wrap.getY() > CHOKE.getY(), "continues from the arc through the centroid");
    }

    @Test
    void theCentreCommitsWhenEveryFlankArrivesOrTheWrapRunsOut() {
        List<Double> farFlank = Arrays.asList(40.0, ContainmentCollapse.FLANK_ARRIVAL_DISTANCE + 1.0);
        List<Double> arrived = Arrays.asList(40.0, (double) ContainmentCollapse.FLANK_ARRIVAL_DISTANCE);

        assertFalse(ContainmentCollapse.wrapComplete(10, farFlank));
        assertTrue(ContainmentCollapse.wrapComplete(10, arrived));
        assertTrue(ContainmentCollapse.wrapComplete(ContainmentCollapse.WRAP_FRAME_CAP, farFlank));
        assertFalse(ContainmentCollapse.wrapComplete(ContainmentCollapse.WRAP_FRAME_CAP - 1, farFlank));
        assertTrue(ContainmentCollapse.wrapComplete(0, Collections.emptyList()), "every flank died");
    }
}
