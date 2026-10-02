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
    void airOnlyStaticDefenceIsNotFixedFire() {
        StaticDefenseZone turret = new StaticDefenseZone(UnitType.Terran_Missile_Turret, TANK, 224);
        StaticDefenseZone spore = new StaticDefenseZone(UnitType.Zerg_Spore_Colony, TANK, 224);
        StaticDefenseZone cannon = new StaticDefenseZone(UnitType.Protoss_Photon_Cannon, TANK, 224);
        StaticDefenseZone sunken = new StaticDefenseZone(UnitType.Zerg_Sunken_Colony, TANK, 224);

        assertEquals(Arrays.asList(cannon, sunken),
                FixedFire.fixedFireZones(Arrays.asList(turret, spore, cannon, sunken)));
        assertFalse(FixedFire.firesFromWhereItStands(UnitType.Terran_Missile_Turret));
        assertFalse(FixedFire.firesFromWhereItStands(UnitType.Zerg_Spore_Colony));
    }

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
    void aSkipIsWrittenOncePerAttackerAndZonePerCooldown() {
        FixedFire fire = new FixedFire();
        fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT);

        assertTrue(fire.firstSkip(7, TANK_ZONE, HURT));
        assertFalse(fire.firstSkip(7, TANK_ZONE, HURT + 1));
        assertTrue(fire.firstSkip(8, TANK_ZONE, HURT + 1));
    }

    @Test
    void aSkipInsideAnotherCoolingZoneIsWrittenForThatZone() {
        FixedFire fire = new FixedFire();
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, INSIDE, 160);
        fire.recordHurt(INSIDE, Arrays.asList(TANK_ZONE, bunker), PADDING, HURT);

        assertTrue(fire.firstSkip(7, TANK_ZONE, HURT));
        assertTrue(fire.firstSkip(7, bunker, HURT));
        assertFalse(fire.firstSkip(7, bunker, HURT));
    }

    @Test
    void aSkipOfAZoneSeenAFewPixelsAwayIsNotWrittenAgain() {
        FixedFire fire = new FixedFire();
        fire.recordHurt(INSIDE, Collections.singletonList(TANK_ZONE), PADDING, HURT);
        StaticDefenseZone seenAgain = new StaticDefenseZone(SIEGED, new Position(TANK.getX() + 8, TANK.getY()),
                TANK_REACH);

        assertTrue(fire.firstSkip(7, TANK_ZONE, HURT));
        assertFalse(fire.firstSkip(7, seenAgain, HURT + 5));
    }

    @Test
    void aCooldownKeptAliveByRepeatedHurtsWritesEachSkipOnce() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        assertTrue(fire.firstSkip(7, TANK_ZONE, HURT));
        int later = HURT + FixedFire.COOLDOWN_FRAMES * 3;
        for (int frame = HURT + 100; frame <= later; frame += 100) {
            fire.recordHurt(INSIDE, zones, PADDING, frame);
            fire.expire(frame);
            assertFalse(fire.firstSkip(7, TANK_ZONE, frame));
        }
    }

    @Test
    void aNewCooldownWritesTheSkipAgain() {
        FixedFire fire = new FixedFire();
        List<StaticDefenseZone> zones = Collections.singletonList(TANK_ZONE);
        fire.recordHurt(INSIDE, zones, PADDING, HURT);
        assertTrue(fire.firstSkip(7, TANK_ZONE, HURT));
        int again = HURT + FixedFire.COOLDOWN_FRAMES + 10;
        fire.expire(again);
        fire.recordHurt(INSIDE, zones, PADDING, again);

        assertTrue(fire.firstSkip(7, TANK_ZONE, again));
    }

    @Test
    void aZoneNotCoolingWritesNoSkip() {
        assertFalse(new FixedFire().firstSkip(7, TANK_ZONE, HURT));
    }

    @Test
    void theSiegedTankZonesAreTheTanksAmongFixedFire() {
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, TANK, 160);
        StaticDefenseZone lurker = new StaticDefenseZone(UnitType.Zerg_Lurker, TANK, 208);

        assertEquals(Collections.singletonList(TANK_ZONE),
                FixedFire.siegedTankZones(Arrays.asList(bunker, TANK_ZONE, lurker)));
    }

    @Test
    void aLurkerWouldFireOnATargetFromInsideTheTanksReach() {
        Position lurker = new Position(TANK.getX(), TANK.getY() + 700);
        Position target = new Position(TANK.getX(), TANK.getY() + 200);
        double distance = lurker.getDistance(target);

        assertSame(TANK_ZONE, FixedFire.firingPointZone(lurker, target, distance, LURKER_RANGE,
                Collections.singletonList(TANK_ZONE), PADDING));
    }

    @Test
    void aTargetFiredOnFromOutsideTheTanksReachIsKept() {
        Position lurker = new Position(TANK.getX(), TANK.getY() + 900);
        Position target = new Position(TANK.getX(), TANK.getY() + 500);
        double distance = lurker.getDistance(target);

        assertNull(FixedFire.firingPointZone(lurker, target, distance, LURKER_RANGE,
                Collections.singletonList(TANK_ZONE), PADDING));
    }

    @Test
    void aTargetFiredOnJustInsideTheReachIsRefused() {
        int firingDistance = TANK_REACH + PADDING - 10;
        Position lurker = new Position(TANK.getX(), TANK.getY() + 900);
        Position target = new Position(TANK.getX(), TANK.getY() + firingDistance - LURKER_RANGE);
        double distance = lurker.getDistance(target);

        assertSame(TANK_ZONE, FixedFire.firingPointZone(lurker, target, distance, LURKER_RANGE,
                Collections.singletonList(TANK_ZONE), PADDING));
    }

    @Test
    void aTargetAlreadyInTheLurkersRangeIsNeverRefused() {
        assertNull(FixedFire.firingPointZone(INSIDE, TANK, LURKER_RANGE, LURKER_RANGE,
                Collections.singletonList(TANK_ZONE), PADDING));
    }

    @Test
    void noTankZonesRefuseNothing() {
        Position lurker = new Position(TANK.getX(), TANK.getY() + 700);

        assertNull(FixedFire.firingPointZone(lurker, TANK, lurker.getDistance(TANK), LURKER_RANGE,
                Collections.emptyList(), PADDING));
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
