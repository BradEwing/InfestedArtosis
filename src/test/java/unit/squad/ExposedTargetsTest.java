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
    void aTargetGivenUpOnIsNotNominatedAgainWithinTheRetryWindow() {
        int now = 12000;
        ExposedTargets.Memory memory = new ExposedTargets.Memory();
        memory.record(new Position(2000, 2000), now);
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Arrays.asList(
                contact(1, UnitType.Terran_Supply_Depot, 2000 + ExposedTargets.SEEK_RADIUS, 2000),
                contact(2, UnitType.Terran_Supply_Depot, 3000, 2000)), 6);

        List<ExposedTargets.Group> admitted = memory.admitted(groups, now + ExposedTargets.RETRY_FRAMES - 1);

        assertEquals(1, admitted.size());
        assertEquals(new Position(3000, 2000), admitted.get(0).getAnchor());
        assertEquals(new Position(3000, 2000), ExposedTargets.choose(
                memory.admitted(groups, now + 1), Collections.emptyList(), 0, FLOCK).getAnchor());
    }

    @Test
    void aTargetGivenUpOnIsNominatedAgainOnceTheRetryWindowPasses() {
        int now = 12000;
        ExposedTargets.Memory memory = new ExposedTargets.Memory();
        Position anchor = new Position(2000, 2000);
        memory.record(anchor, now);

        assertFalse(memory.admits(anchor, now + ExposedTargets.RETRY_FRAMES - 1));
        assertTrue(memory.admits(anchor, now + ExposedTargets.RETRY_FRAMES));
        assertTrue(memory.admits(new Position(2001 + ExposedTargets.SEEK_RADIUS, 2000), now));
        memory.record(null, now);
        assertTrue(memory.admits(anchor, now));
    }

    @Test
    void twoQuicklyKilledAntiAirUnitsInOneGroupDefendEachOther() {
        int flock = 10;
        List<ExposedTargets.Group> groups = ExposedTargets.groups(Arrays.asList(
                contact(5, UnitType.Terran_Goliath, 2000, 2000),
                contact(6, UnitType.Terran_Goliath, 2100, 2000)), flock);
        List<AirHarassTargeting.AirThreat> threats = Arrays.asList(
                threat(5, UnitType.Terran_Goliath, 2000, 2000),
                threat(6, UnitType.Terran_Goliath, 2100, 2000));

        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getIsolatedAntiAirIds().size());
        assertEquals(AirHarassTargeting.defenseAt(threats, groups.get(0).getAnchor(),
                AirHarassEvaluator.STRIKE_RADIUS), ExposedTargets.defenseAt(groups.get(0), threats), 1e-9);
        assertNull(ExposedTargets.choose(groups, threats, 0, FLOCK));
    }
}
