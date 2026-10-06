package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassDefenseZonesTest {

    private static final int NOW = 12000;
    private static final Position TRIGGER_AT = new Position(2000, 2000);

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static AirHarassTargeting.AirThreat goliath(int id, int dx, int dy) {
        return threat(id, UnitType.Terran_Goliath, new Position(TRIGGER_AT.getX() + dx, TRIGGER_AT.getY() + dy));
    }

    private static List<AirHarassTargeting.AirThreat> list(AirHarassTargeting.AirThreat... threats) {
        return Arrays.asList(threats);
    }

    private static List<Integer> ids(Collection<AirHarassTargeting.AirThreat> threats) {
        return threats.stream().map(AirHarassTargeting.AirThreat::getId).sorted().collect(Collectors.toList());
    }

    private static List<AirHarassTargeting.AirThreat> none() {
        return Collections.emptyList();
    }

    @Test
    void aRecordedZoneHoldsTheTriggerAndEveryAntiAirWithinTheMemberRadiusButNotFartherUnits() {
        AirHarassTargeting.AirThreat trigger = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat near = goliath(2, 300, 0);
        AirHarassTargeting.AirThreat far = goliath(3, AirHarassDefenseZones.MEMBER_RADIUS + 400, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();

        AirHarassDefenseZones.Zone zone = zones.record(trigger, list(trigger, near, far), NOW);

        assertEquals(1, zones.size());
        assertEquals(TRIGGER_AT, zone.getCenter());
        assertEquals(trigger.getReach() + AirHarassDefenseZones.PADDING, zone.getRadius());
        assertEquals(Arrays.asList(1, 2), ids(zones.remembered(none())));
    }

    @Test
    void aMemberCoveringTheTriggerJoinsTheZoneBeyondTheMemberRadius() {
        AirHarassTargeting.AirThreat trigger = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat bunker = new AirHarassTargeting.AirThreat(2, UnitType.Terran_Bunker,
                new Position(TRIGGER_AT.getX() + AirHarassDefenseZones.MEMBER_RADIUS + 40, TRIGGER_AT.getY()), 400,
                1);
        assertTrue(bunker.covers(trigger.getPosition(), AirHarassEvaluator.STRIKE_RADIUS));
        AirHarassDefenseZones zones = new AirHarassDefenseZones();

        zones.record(trigger, list(trigger, bunker), NOW);

        assertEquals(Arrays.asList(1, 2), ids(zones.remembered(none())));
    }

    @Test
    void aSecondRecordOverTheSameGroundExtendsTheZoneInsteadOfOpeningAnother() {
        AirHarassTargeting.AirThreat first = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat second = goliath(2, 100, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();

        zones.record(first, list(first), NOW);
        zones.record(second, list(first, second), NOW + 200);

        assertEquals(1, zones.size());
        assertEquals(Arrays.asList(1, 2), ids(zones.remembered(none())));
    }

    @Test
    void theRememberedAntiAirLeavesOutUnitsKnownNowAndPricesTheGroundTheFlockWouldEnter() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat b = goliath(2, 60, 0);
        AirHarassTargeting.AirThreat c = goliath(3, 0, 60);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a, b, c), NOW);
        double tolerance = AirHarassTargeting.defenseAt(list(a), TRIGGER_AT, 0) * 1.5;

        assertEquals(Arrays.asList(2, 3), ids(zones.remembered(list(a))));
        List<AirHarassTargeting.AirThreat> priced = zones.remembered(none());
        assertTrue(AirHarassTargeting.defenseAt(priced, TRIGGER_AT, AirHarassEvaluator.STRIKE_RADIUS) > tolerance);
        assertFalse(AirHarassTargeting.avoided(priced, tolerance).isEmpty());
        assertTrue(AirHarassTargeting.avoided(list(a), tolerance).isEmpty());
    }

    @Test
    void aZoneStaysWhileItsGroundIsNotInSightEvenWithNoUnitKnown() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a), NOW);

        int dropped = zones.refresh(none(), point -> false, NOW + 2000);

        assertEquals(0, dropped);
        assertEquals(1, zones.size());
        assertEquals(Collections.singletonList(1), ids(zones.remembered(none())));
    }

    @Test
    void aZoneClearsWhenItsGroundIsSeenAndNoneOfItsAntiAirIsKnown() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat b = goliath(2, 60, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a, b), NOW);

        int dropped = zones.refresh(none(), point -> true, NOW + 100);

        assertEquals(1, dropped);
        assertEquals(0, zones.size());
        assertTrue(zones.remembered(none()).isEmpty());
    }

    @Test
    void aSeenZoneKeepsTheMembersStillKnownAtItsGroundAndMovesThemToWhereTheyStand() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat b = goliath(2, 60, 0);
        AirHarassTargeting.AirThreat movedB = goliath(2, 120, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a, b), NOW);

        zones.refresh(list(movedB), point -> true, NOW + 100);

        List<AirHarassTargeting.AirThreat> remembered = zones.remembered(none());
        assertEquals(Collections.singletonList(2), ids(remembered));
        assertEquals(movedB.getPosition(), remembered.get(0).getPosition());
    }

    @Test
    void aMemberKnownFarFromTheZoneHasLeftItAndAZoneWithNoMemberLeftIsDropped() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat away = goliath(1, 3000, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a), NOW);

        int dropped = zones.refresh(list(away), point -> false, NOW + 100);

        assertEquals(1, dropped);
        assertEquals(0, zones.size());
    }

    @Test
    void aDeadUnitLeavesItsZoneAndAZoneLeftEmptyIsDropped() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassTargeting.AirThreat b = goliath(2, 60, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a, b), NOW);

        zones.forget(1);
        assertEquals(Collections.singletonList(2), ids(zones.remembered(none())));

        zones.forget(2);
        assertEquals(0, zones.size());
    }

    @Test
    void aZoneNeverSeenClearIsDroppedOnceItIsOlderThanTheMaximumAge() {
        AirHarassTargeting.AirThreat a = goliath(1, 0, 0);
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(a, list(a), NOW);

        assertEquals(0, zones.refresh(none(), point -> false, NOW + AirHarassDefenseZones.MAX_AGE_FRAMES));
        assertEquals(1, zones.refresh(none(), point -> false, NOW + AirHarassDefenseZones.MAX_AGE_FRAMES + 1));
    }

    @Test
    void aZoneMemberTheFlockSightsJustOutsideItsReachIsNoNewAntiAirOnceAcceptedButAStrangerStillIs() {
        Position flock = new Position(3500, 1000);
        AirHarassTargeting.AirThreat member = threat(1, UnitType.Terran_Goliath,
                new Position(flock.getX() + 231 + AirHarassScouting.EXIT_MARGIN - 25, flock.getY()));
        AirHarassTargeting.AirThreat stranger = threat(2, UnitType.Terran_Goliath,
                new Position(flock.getX(), flock.getY() + 231 + AirHarassScouting.EXIT_MARGIN - 25));
        double tolerance = member.getStrength() / 2;
        AirHarassDefenseZones zones = new AirHarassDefenseZones();
        zones.record(member, list(member), NOW);
        AirHarassState state = new AirHarassState(NOW, 600);
        state.acceptIds(zones.memberIds(), NOW, 600);

        assertEquals(Collections.singleton(1), zones.memberIds());
        assertNull(AirHarassScouting.react(state, list(member), null, flock, tolerance, NOW + 1, 600));
        assertEquals(stranger, AirHarassScouting.react(state, list(member, stranger), null, flock, tolerance,
                NOW + 2, 600).getTrigger());
    }

    @Test
    void interceptorsNearTheTriggerDoNotJoinTheZone() {
        AirHarassTargeting.AirThreat trigger = threat(1, UnitType.Protoss_Corsair, TRIGGER_AT);
        AirHarassTargeting.AirThreat interceptor = threat(2, UnitType.Protoss_Interceptor,
                new Position(TRIGGER_AT.getX() + 50, TRIGGER_AT.getY()));
        AirHarassDefenseZones zones = new AirHarassDefenseZones();

        zones.record(trigger, list(trigger, interceptor), NOW);

        assertEquals(Collections.singletonList(1), ids(zones.remembered(none())));
    }
}
