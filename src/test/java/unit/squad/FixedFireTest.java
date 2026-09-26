package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedFireTest {

    private static final UnitType SIEGED = UnitType.Terran_Siege_Tank_Siege_Mode;
    private static final int TANK_REACH = 400;
    private static final Position TANK = new Position(2461, 373);
    private static final StaticDefenseZone TANK_ZONE = new StaticDefenseZone(SIEGED, TANK, TANK_REACH);
    private static final Position INSIDE = new Position(2461, 673);
    private static final Position OUTSIDE = new Position(2461, 1100);
    private static final int PADDING = 48;
    private static final int LURKER_RANGE = UnitType.Zerg_Lurker.groundWeapon().maxRange();
    private static final int HURT = 10000;

    @Test
    void fixedFireIsBuildingsSiegedTanksAndLurkers() {
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, TANK, 160);
        StaticDefenseZone lurker = new StaticDefenseZone(UnitType.Zerg_Lurker, TANK, 208);
        StaticDefenseZone marine = new StaticDefenseZone(UnitType.Terran_Marine, TANK, 128);
        StaticDefenseZone hurtMark = new StaticDefenseZone(UnitType.None, TANK, 64);

        List<StaticDefenseZone> kept = FixedFire.fixedFireZones(Arrays.asList(TANK_ZONE, bunker, lurker, marine,
                hurtMark));

        assertEquals(Arrays.asList(TANK_ZONE, bunker, lurker), kept);
    }

    @Test
    void aHurtInsideAZoneStartsItsCooldown() {
        FixedFire fire = new FixedFire();

        List<StaticDefenseZone> started = fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT);

        assertEquals(Collections.singletonList(TANK_ZONE), started);
        assertTrue(fire.isCooling(TANK_ZONE, HURT));
    }

    @Test
    void aHurtOutsideEveryZoneStartsNothing() {
        FixedFire fire = new FixedFire();

        assertTrue(fire.recordHurt(OUTSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT).isEmpty());
        assertFalse(fire.isCooling(TANK_ZONE, HURT));
    }

    @Test
    void theCooldownRunsItsWindowFromTheLastHurt() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        int again = HURT + 100;

        assertTrue(fire.recordHurt(INSIDE, zones, PADDING, again).isEmpty());
        assertTrue(fire.isCooling(TANK_ZONE, again + FixedFire.COOLDOWN_FRAMES - 1));
        assertFalse(fire.isCooling(TANK_ZONE, again + FixedFire.COOLDOWN_FRAMES));
    }

    @Test
    void aZoneSeenAFewPixelsAwayIsTheSameZone() {
        FixedFire fire = new FixedFire();
        fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT);
        StaticDefenseZone nudged = new StaticDefenseZone(SIEGED, new Position(TANK.getX() + 8, TANK.getY()),
                TANK_REACH);
        StaticDefenseZone otherTank = new StaticDefenseZone(SIEGED, new Position(TANK.getX() + 200, TANK.getY()),
                TANK_REACH);

        assertTrue(fire.isCooling(nudged, HURT));
        assertFalse(fire.isCooling(otherTank, HURT));
        assertEquals(Collections.singletonList(nudged), fire.coolingZones(Arrays.asList(nudged, otherTank), HURT));
    }

    @Test
    void expireDropsARunOutCooldown() {
        FixedFire fire = new FixedFire();
        fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT);

        fire.expire(HURT + FixedFire.COOLDOWN_FRAMES);

        assertEquals(Collections.singletonList(TANK_ZONE),
                fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING,
                        HURT + FixedFire.COOLDOWN_FRAMES));
    }

    @Test
    void aTargetInsideACoolingZoneAndOutOfOwnRangeIsSkipped() {
        StaticDefenseZone skipping = FixedFire.skippingZone(TANK, 300, LURKER_RANGE,
                Collections.singletonList(TANK_ZONE), PADDING, false);

        assertSame(TANK_ZONE, skipping);
    }

    @Test
    void aCommittingSquadSkipsNothing() {
        assertNull(FixedFire.skippingZone(TANK, 300, LURKER_RANGE, Collections.singletonList(TANK_ZONE), PADDING,
                true));
    }

    @Test
    void aTargetAlreadyInOwnRangeIsKept() {
        assertNull(FixedFire.skippingZone(TANK, LURKER_RANGE, LURKER_RANGE, Collections.singletonList(TANK_ZONE),
                PADDING, false));
    }

    @Test
    void aTargetOutsideEveryCoolingZoneIsKept() {
        assertNull(FixedFire.skippingZone(OUTSIDE, 300, LURKER_RANGE, Collections.singletonList(TANK_ZONE), PADDING,
                false));
    }

    @Test
    void aSkipIsWrittenOncePerAttackerAndTargetPerCooldown() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        int start = fire.cooldownStart(TANK_ZONE, HURT);

        assertEquals(HURT, start);
        assertTrue(fire.firstSkip(7, 42, start));
        assertFalse(fire.firstSkip(7, 42, start));
        assertTrue(fire.firstSkip(8, 42, start));
    }

    @Test
    void aCooldownKeptAliveByRepeatedHurtsWritesEachSkipOnce() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        assertTrue(fire.firstSkip(7, 42, fire.cooldownStart(TANK_ZONE, HURT)));
        int later = HURT + FixedFire.COOLDOWN_FRAMES * 3;
        for (int frame = HURT + 100; frame <= later; frame += 100) {
            fire.recordHurt(INSIDE, zones, PADDING, frame);
            fire.expire(frame);
        }

        assertEquals(HURT, fire.cooldownStart(TANK_ZONE, later));
        assertFalse(fire.firstSkip(7, 42, fire.cooldownStart(TANK_ZONE, later)));
    }

    @Test
    void aNewCooldownWritesTheSkipAgain() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        assertTrue(fire.firstSkip(7, 42, fire.cooldownStart(TANK_ZONE, HURT)));
        int again = HURT + FixedFire.COOLDOWN_FRAMES + 10;
        fire.expire(again);
        fire.recordHurt(INSIDE, zones, PADDING, again);

        assertEquals(again, fire.cooldownStart(TANK_ZONE, again));
        assertTrue(fire.firstSkip(7, 42, fire.cooldownStart(TANK_ZONE, again)));
    }

    @Test
    void aZoneNotCoolingHasNoCooldownStart() {
        assertEquals(-1, new FixedFire().cooldownStart(TANK_ZONE, HURT));
    }

    @Test
    void theCooldownAppliesToGroundUnitsOnly() {
        assertTrue(FixedFire.appliesTo(UnitType.Zerg_Lurker));
        assertTrue(FixedFire.appliesTo(UnitType.Zerg_Zergling));
        assertFalse(FixedFire.appliesTo(UnitType.Zerg_Mutalisk));
        assertFalse(FixedFire.appliesTo(UnitType.Zerg_Scourge));
    }

    @Test
    void aFighterClearOfTheFireWaitsWhereItStands() {
        assertSame(OUTSIDE, FixedFire.waitPoint(OUTSIDE, Collections.singletonList(TANK_ZONE), PADDING,
                point -> false));
    }

    @Test
    void aFighterInsideTheFireWaitsAtAClearPoint() {
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);

        Position wait = FixedFire.waitPoint(INSIDE, zones, PADDING, point -> true);

        assertNotNull(wait);
        assertFalse(TANK_ZONE.covers(wait, PADDING));
    }

    @Test
    void aFighterBoxedInInsideTheFireHasNoWaitPoint() {
        assertNull(FixedFire.waitPoint(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, point -> false));
    }

    @Test
    void theHoldPointIsClearOfTheTanksReach() {
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);

        Position hold = FixedFire.holdPoint(INSIDE, zones, PADDING, point -> true);

        assertNotNull(hold);
        assertTrue(RunbyTargeting.zoneMargin(hold, zones, PADDING) >= 0);
        assertFalse(TANK_ZONE.covers(hold, PADDING));
    }

    @Test
    void noHoldPointWhenNoStepGainsGround() {
        assertNull(FixedFire.holdPoint(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, point -> false));
    }

    @Test
    void theCoveringZoneIsTheLongestReaching() {
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, INSIDE, 160);

        assertSame(TANK_ZONE, FixedFire.coveringZone(INSIDE, Arrays.asList(bunker, TANK_ZONE), PADDING));
        assertNull(FixedFire.coveringZone(OUTSIDE, Arrays.asList(bunker, TANK_ZONE), PADDING));
    }
}
