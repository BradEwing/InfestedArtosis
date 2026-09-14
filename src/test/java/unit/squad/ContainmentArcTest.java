package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentArcTest {

    private static final Position CHOKE = new Position(1600, 1600);
    private static final Position ENEMY_MAIN = new Position(1600, 2240);
    private static final Position FACE_TARGET = new Position(1600, 960);
    private static final int ARC_RADIUS = 160;
    private static final int ARC_DEGREES = 90;
    private static final int NUM_POINTS = 8;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final int MARINE_RANGE = UnitType.Terran_Marine.groundWeapon().maxRange();
    private static final List<UnitType> LING_LURKER = Arrays.asList(UnitType.Zerg_Zergling, UnitType.Zerg_Lurker);

    private static StaticDefenseZone bunkerAt(Position position) {
        return new StaticDefenseZone(UnitType.Terran_Bunker, position, MARINE_RANGE);
    }

    private static Arc computeArc(List<StaticDefenseZone> zones, int padding) {
        Arc arc = new Arc(CHOKE, FACE_TARGET, ARC_RADIUS, ARC_DEGREES, NUM_POINTS);
        arc.compute(ALL_WALKABLE, zones, padding, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static void assertNoPointWithinReachPlusPadding(Arc arc, List<StaticDefenseZone> zones, int padding) {
        assertFalse(arc.isEmpty());
        for (Position point : arc.getPositions()) {
            for (StaticDefenseZone zone : zones) {
                assertTrue(zone.edgeDistance(point.getX(), point.getY()) > zone.getReach() + padding,
                        point + " is within reach plus padding of the bunker at " + zone.getCenter());
            }
        }
    }

    @Test
    void noArcPointLiesWithinReachPlusPaddingOfABunkerAtTheChoke() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        List<StaticDefenseZone> zones = Collections.singletonList(bunkerAt(CHOKE));

        assertNoPointWithinReachPlusPadding(computeArc(zones, padding), zones, padding);
    }

    @Test
    void noArcPointLiesWithinReachPlusPaddingOfBunkersOnTheArcLine() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        List<StaticDefenseZone> zones = Arrays.asList(
                bunkerAt(new Position(CHOKE.getX(), CHOKE.getY() - ARC_RADIUS)),
                bunkerAt(new Position(CHOKE.getX() - 112, CHOKE.getY() - 112)),
                bunkerAt(new Position(CHOKE.getX() + 112, CHOKE.getY() - 112)));

        assertNoPointWithinReachPlusPadding(computeArc(zones, padding), zones, padding);
    }

    @Test
    void theArcFacesAwayFromTheEnemyMain() {
        Arc arc = computeArc(Collections.emptyList(), 0);

        for (Position point : arc.getPositions()) {
            assertTrue(point.getDistance(ENEMY_MAIN) > CHOKE.getDistance(ENEMY_MAIN));
        }
    }

    @Test
    void anArcWithNoPointClearOfCoverageIsEmpty() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        StaticDefenseZone deepCoverage = new StaticDefenseZone(UnitType.Terran_Bunker, CHOKE, 1024);

        assertTrue(computeArc(Collections.singletonList(deepCoverage), padding).isEmpty());
    }

    @Test
    void paddingIsTheLargestMemberExtentPlusAMargin() {
        int lingExtent = maxDimension(UnitType.Zerg_Zergling);
        int lurkerExtent = maxDimension(UnitType.Zerg_Lurker);
        int margin = SquadManager.containmentDefensePadding(Collections.emptyList());

        assertTrue(margin > 0);
        assertEquals(lingExtent + margin,
                SquadManager.containmentDefensePadding(Collections.singletonList(UnitType.Zerg_Zergling)));
        assertEquals(Math.max(lingExtent, lurkerExtent) + margin,
                SquadManager.containmentDefensePadding(LING_LURKER));
    }

    private static int maxDimension(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }
}
