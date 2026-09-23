package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentPushbackTest {

    private static final Position CHOKE = new Position(1600, 1600);
    private static final Position ENEMY_MAIN = new Position(1600, 2240);
    private static final Position FACE_TARGET = new Position(1600, 960);
    private static final int ARC_RADIUS = 160;
    private static final int ARC_DEGREES = 90;
    private static final int MEMBERS = 12;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final int PADDING = SquadManager.containmentDefensePadding(
            Collections.singletonList(UnitType.Zerg_Zergling));

    private static int baseRange(UnitType type) {
        return ContainmentPushback.groundReach(type, WeaponType::maxRange);
    }

    private static StaticDefenseZone firing(UnitType type, Position position) {
        return new StaticDefenseZone(type, position, baseRange(type));
    }

    private static Arc heldArc() {
        Arc arc = new Arc(CHOKE, FACE_TARGET, ARC_RADIUS, ARC_DEGREES, MEMBERS);
        arc.compute(ALL_WALKABLE, Collections.emptyList(), PADDING, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static Arc pushBack(Arc current, List<StaticDefenseZone> zones) {
        return ContainmentPushback.pushBack(current, zones, PADDING, ALL_WALKABLE, MAP_PIXELS, MAP_PIXELS);
    }

    private static void assertEveryPointOutOfReach(Arc arc, List<StaticDefenseZone> zones) {
        for (Position point : arc.getPositions()) {
            for (StaticDefenseZone zone : zones) {
                assertTrue(zone.edgeDistance(point.getX(), point.getY()) > zone.getReach() + PADDING,
                        point + " is within reach plus margin of the " + zone.getStructure() + " at "
                                + zone.getCenter());
            }
        }
    }

    @Test
    void rangedUnitsAndABunkerOutrangeAZergling() {
        int ling = baseRange(UnitType.Zerg_Zergling);

        assertTrue(ContainmentPushback.outranges(baseRange(UnitType.Terran_Marine), ling));
        assertTrue(ContainmentPushback.outranges(baseRange(UnitType.Protoss_Dragoon), ling));
        assertTrue(ContainmentPushback.outranges(baseRange(UnitType.Terran_Siege_Tank_Siege_Mode), ling));
        assertTrue(ContainmentPushback.outranges(baseRange(UnitType.Terran_Bunker), ling));
        assertFalse(ContainmentPushback.outranges(baseRange(UnitType.Protoss_Zealot), ling));
        assertFalse(ContainmentPushback.outranges(baseRange(UnitType.Zerg_Zergling), ling));
    }

    @Test
    void aBunkerReachesAsFarAsTheMarinesInsideIt() {
        assertEquals(UnitType.Terran_Marine.groundWeapon().maxRange(), baseRange(UnitType.Terran_Bunker));
    }

    @Test
    void aUnitWithNoGroundWeaponHasNoReach() {
        assertEquals(0, baseRange(UnitType.Terran_Medic));
    }

    @Test
    void thePushedArcPlacesEveryMemberOutsideReachPlusMargin() {
        Arc current = heldArc();
        List<StaticDefenseZone> zones = Arrays.asList(
                firing(UnitType.Protoss_Dragoon, new Position(1600, 1480)),
                firing(UnitType.Terran_Marine, new Position(1500, 1520)));
        assertTrue(ContainmentPushback.covers(current, zones, PADDING), "the held arc must start inside reach");

        Arc pushed = pushBack(current, zones);

        assertNotNull(pushed);
        assertEveryPointOutOfReach(pushed, zones);
        assertTrue(pushed.size() >= Math.min(MEMBERS, current.size()),
                "every member keeps a point of its own on the pushed arc");
        assertTrue(pushed.getRadius() > current.getRadius());
        assertTrue(pushed.getMidpoint().getDistance(ENEMY_MAIN) > current.getMidpoint().getDistance(ENEMY_MAIN),
                "the arc moves back, away from the enemy");
    }

    @Test
    void aSiegeTankOnTheChokePushesTheArcOutOfItsReach() {
        Arc current = heldArc();
        List<StaticDefenseZone> zones = Collections.singletonList(
                firing(UnitType.Terran_Siege_Tank_Siege_Mode, CHOKE));

        Arc pushed = pushBack(current, zones);

        assertNotNull(pushed);
        assertEveryPointOutOfReach(pushed, zones);
    }

    @Test
    void withNoPointOutOfReachThereIsNoArcAndTheSquadRetreats() {
        Arc current = heldArc();
        StaticDefenseZone coversEveryRadius = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode, CHOKE,
                ContainmentPushback.MAX_RADIUS * 4);

        Arc pushed = pushBack(current, Collections.singletonList(coversEveryRadius));

        assertNull(pushed);
        assertEquals(SquadManager.ContainmentVerdict.RETREAT,
                SquadManager.containmentVerdict(false, false, pushed == null, true, true, false, false, true));
    }

    @Test
    void anArcAlreadyAtTheLimitCannotBePushedFurther() {
        Arc atLimit = new Arc(CHOKE, FACE_TARGET, ContainmentPushback.MAX_RADIUS, ARC_DEGREES, MEMBERS);
        atLimit.compute(ALL_WALKABLE, Collections.emptyList(), PADDING, MAP_PIXELS, MAP_PIXELS);

        assertNull(pushBack(atLimit, Collections.singletonList(firing(UnitType.Protoss_Dragoon, CHOKE))));
    }

    @Test
    void anArcClearOfEveryReachIsNotCovered() {
        List<StaticDefenseZone> zones = Collections.singletonList(
                firing(UnitType.Terran_Marine, new Position(1600, 2200)));

        assertFalse(ContainmentPushback.covers(heldArc(), zones, PADDING));
    }
}
