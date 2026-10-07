package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.managed.Lurker;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LurkerWithdrawTest {

    @Test
    void aBurrowedContainingLurkerHitWithNothingInReachWithdraws() {
        boolean hit = ManagedUnit.isOutrangedHit(100, 80, UnitRole.CONTAIN, false);

        assertTrue(hit);
        assertTrue(SquadManager.evadeGateOpen(false, false, Lurker.canWithdraw(UnitRole.CONTAIN, true, true),
                UnitRole.CONTAIN, false));
    }

    @Test
    void aBurrowedLurkerThatCannotUnburrowDoesNotWithdraw() {
        assertFalse(SquadManager.evadeGateOpen(false, false, Lurker.canWithdraw(UnitRole.CONTAIN, true, false),
                UnitRole.CONTAIN, false));
    }

    @Test
    void aCollapsingLurkerDoesNotWithdraw() {
        assertFalse(SquadManager.evadeGateOpen(true, false, true, UnitRole.CONTAIN, false));
    }

    private static final Position FROM = new Position(1000, 1000);
    private static final Position RALLY_WEST = new Position(-1000, 1000);
    private static final Position CONTAIN_EAST = new Position(1100, 1000);
    private static final int PADDING = 48;

    private static double degreesOff(Position from, Position to, Position bearingTo) {
        double actual = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        double wanted = Math.atan2(bearingTo.getY() - from.getY(), bearingTo.getX() - from.getX());
        double diff = Math.abs(Math.toDegrees(actual - wanted)) % 360;
        return diff > 180 ? 360 - diff : diff;
    }

    @Test
    void anUnattributedHitWithdrawsStraightBackTowardTheRallyAtTheMinimumStep() {
        Position point = SquadManager.withdrawDestination(FROM, Collections.emptyList(), PADDING, 384, RALLY_WEST,
                position -> true, CONTAIN_EAST);

        assertTrue(FROM.getDistance(point) >= SquadManager.withdrawMinStep(384, PADDING));
        assertTrue(degreesOff(FROM, point, RALLY_WEST) <= 15);
    }

    @Test
    void anUnattributedHitWithNoWalkableRallySideTakesNoLongStepTowardTheEnemy() {
        Position point = SquadManager.withdrawDestination(FROM, Collections.emptyList(), PADDING, 384, RALLY_WEST,
                position -> position.getX() > FROM.getX(), CONTAIN_EAST);

        assertEquals(RunbyTargeting.CLEAR_MIN_RADIUS, (int) Math.round(FROM.getDistance(point)));
    }

    @Test
    void noLearnedReachKeepsTheNearestRing() {
        Position point = SquadManager.withdrawDestination(FROM, Collections.emptyList(), PADDING, 0, RALLY_WEST,
                position -> true, CONTAIN_EAST);

        assertTrue(FROM.getDistance(point) < SquadManager.withdrawMinStep(384, PADDING));
    }

    @Test
    void noRallyPointKeepsTheNearestRingTowardTheSeekPoint() {
        Position point = SquadManager.withdrawDestination(FROM, Collections.emptyList(), PADDING, 384, null,
                position -> true, CONTAIN_EAST);

        assertEquals(RunbyTargeting.CLEAR_MIN_RADIUS, (int) Math.round(FROM.getDistance(point)));
        assertTrue(point.getX() > FROM.getX());
    }

    @Test
    void aCoveringZoneIsStillClearedByTheClearance() {
        StaticDefenseZone tank = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode,
                new Position(1400, 1000), 384);
        List<StaticDefenseZone> zones = Collections.singletonList(tank);
        assertEquals(1, SquadManager.coveringZones(zones, FROM, PADDING));

        Position point = SquadManager.withdrawDestination(FROM, zones, PADDING, 384, RALLY_WEST, position -> true,
                CONTAIN_EAST);

        assertTrue(RunbyTargeting.zoneMargin(point, zones, PADDING) >= SquadManager.WITHDRAW_CLEARANCE);
    }

    @Test
    void onlyZonesThatCoverThePositionAreCounted() {
        StaticDefenseZone near = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1100, 1000), 192);
        StaticDefenseZone far = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(5000, 1000), 192);

        assertEquals(1, SquadManager.coveringZones(Arrays.asList(near, far), FROM, PADDING));
        assertEquals(0, SquadManager.coveringZones(Collections.singletonList(far), FROM, PADDING));
    }
}
