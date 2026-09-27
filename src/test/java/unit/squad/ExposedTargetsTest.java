package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExposedTargetsTest {

    private static final Position FLOCK = new Position(1000, 1000);

    private static AirHarassTargeting.Contact contact(int id, UnitType type, int x, int y) {
        return new AirHarassTargeting.Contact(id, type, new Position(x, y), type.maxHitPoints() + type.maxShields(),
                1.0);
    }

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, int x, int y) {
        return AirHarassTargeting.AirThreat.of(id, type, new Position(x, y),
                AirHarassTargeting.airRange(type, WeaponType::maxRange));
    }

    @Test
    void aLooseBuildingAwayFromAnyBaseIsNominatedWhenNoAntiAirCoversIt() {
        List<ExposedTargets.Group> groups = ExposedTargets.groups(
                Collections.singletonList(contact(7, UnitType.Terran_Supply_Depot, 2000, 2000)), 6);

        ExposedTargets.Group chosen = ExposedTargets.choose(groups, Collections.emptyList(), 0, FLOCK);

        assertEquals(new Position(2000, 2000), chosen.getAnchor());
        assertEquals(1, chosen.getMembers());
    }

    @Test
    void aGroupUnderMoreAntiAirThanTheFlockToleratesIsNotNominated() {
        List<ExposedTargets.Group> groups = ExposedTargets.groups(
                Collections.singletonList(contact(7, UnitType.Terran_Supply_Depot, 2000, 2000)), 6);
        List<AirHarassTargeting.AirThreat> turret = Collections.singletonList(
                threat(20, UnitType.Terran_Missile_Turret, 2060, 2000));
        double defense = ExposedTargets.defenseAt(groups.get(0), turret);

        assertTrue(defense > 0);
        assertNull(ExposedTargets.choose(groups, turret, defense - 1, FLOCK));
        assertSame(groups.get(0), ExposedTargets.choose(groups, turret, defense, FLOCK));
    }

    @Test
    void contactsWithinTheGroupRadiusFormOneGroupAnchoredAtTheirCentroid() {
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Arrays.asList(
                contact(1, UnitType.Terran_SCV, 2000, 2000),
                contact(2, UnitType.Terran_SCV, 2000 + ExposedTargets.GROUP_RADIUS, 2000),
                contact(3, UnitType.Terran_SCV, 2000 + 3 * ExposedTargets.GROUP_RADIUS, 2000)), 6);

        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).getMembers());
        assertEquals(new Position(2000 + ExposedTargets.GROUP_RADIUS / 2, 2000), groups.get(0).getAnchor());
        assertEquals(2.0 * (AirHarassTargeting.Tier.WORKER.ordinal() + 1), groups.get(0).getValue(), 1e-9);
        assertEquals(1, groups.get(1).getMembers());
    }

    @Test
    void anExposedGoliathTheFlockKillsQuicklyDoesNotDefendItsOwnGroup() {
        int flock = 10;
        AirHarassTargeting.Contact goliath = contact(5, UnitType.Terran_Goliath, 2000, 2000);
        assertEquals(AirHarassTargeting.Tier.ISOLATED_AA, AirHarassTargeting.tier(goliath, flock));
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Collections.singletonList(goliath), flock);
        AirHarassTargeting.AirThreat own = threat(5, UnitType.Terran_Goliath, 2000, 2000);
        AirHarassTargeting.AirThreat escort = threat(6, UnitType.Terran_Goliath, 2300, 2000);

        assertSame(groups.get(0), ExposedTargets.choose(groups, Collections.singletonList(own), 0, FLOCK));
        assertNull(ExposedTargets.choose(groups, Arrays.asList(own, escort), 0, FLOCK));
    }

    @Test
    void anythingTheHarassWouldNotAttackIsNotGrouped() {
        int flock = 3;
        AirHarassTargeting.Contact goliath = contact(5, UnitType.Terran_Goliath, 2000, 2000);
        assertNull(AirHarassTargeting.tier(goliath, flock));

        assertTrue(ExposedTargets.groups(Collections.singletonList(goliath), flock).isEmpty());
    }

    @Test
    void theNearerOfTwoEqualGroupsIsChosenAndValueOutweighsModestDistance() {
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Arrays.asList(
                contact(1, UnitType.Terran_Supply_Depot, 3000, 1000),
                contact(2, UnitType.Terran_Supply_Depot, 1500, 1000)), 6);

        assertEquals(new Position(1500, 1000),
                ExposedTargets.choose(groups, Collections.emptyList(), 0, FLOCK).getAnchor());
        assertEquals(0.5 * groups.get(0).getValue(), ExposedTargets.score(groups.get(0),
                new Position(3000 - (int) ExposedTargets.DISTANCE_SCALE, 1000)), 1e-9);
    }

    @Test
    void anExposedTargetIsFollowedWithinTheSeekRadiusAndLostBeyondIt() {
        Position anchor = new Position(2000, 2000);
        List<ExposedTargets.Group> near = ExposedTargets.groups(Collections.singletonList(
                contact(1, UnitType.Terran_Vulture, 2000 + ExposedTargets.SEEK_RADIUS, 2000)), 6);
        List<ExposedTargets.Group> far = ExposedTargets.groups(Collections.singletonList(
                contact(1, UnitType.Terran_Vulture, 2001 + ExposedTargets.SEEK_RADIUS, 2000)), 6);

        assertSame(near.get(0), ExposedTargets.follow(near, anchor));
        assertNull(ExposedTargets.follow(far, anchor));
    }

    @Test
    void aRetargetSkipsTheGroupItIsLeaving() {
        Position leaving = new Position(2000, 2000);
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Arrays.asList(
                contact(1, UnitType.Terran_Supply_Depot, 2100, 2000),
                contact(2, UnitType.Terran_Supply_Depot, 3000, 2000)), 6);

        List<ExposedTargets.Group> away = ExposedTargets.groupsAwayFrom(groups, leaving);

        assertEquals(1, away.size());
        assertEquals(new Position(3000, 2000), away.get(0).getAnchor());
        assertEquals(2, ExposedTargets.groupsAwayFrom(groups, null).size());
        assertFalse(away.contains(groups.get(0)));
    }
}
