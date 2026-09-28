package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndgameHuntTest {

    private static final Position SQUAD = new Position(1000, 1000);
    private static final Position NEAR = new Position(1100, 1000);
    private static final Position FAR = new Position(2000, 1000);

    @Test
    void assimilatorIsAnEndgameTargetForGroundAttackers() {
        assertTrue(EndgameHunt.isHuntTarget(UnitType.Protoss_Assimilator, false,
                EndgameHunt.hitsGround(UnitType.Zerg_Zergling), EndgameHunt.hitsAir(UnitType.Zerg_Zergling)));
        assertTrue(EndgameHunt.isHuntTarget(UnitType.Terran_Refinery, false, true, false));
        assertTrue(EndgameHunt.isHuntTarget(UnitType.Zerg_Extractor, false, true, false));
    }

    @Test
    void liftedBuildingIsAnEndgameTargetForAntiAir() {
        assertTrue(EndgameHunt.isHuntTarget(UnitType.Terran_Barracks, true,
                EndgameHunt.hitsGround(UnitType.Zerg_Hydralisk), EndgameHunt.hitsAir(UnitType.Zerg_Hydralisk)));
        assertTrue(EndgameHunt.isHuntTarget(UnitType.Terran_Engineering_Bay, true,
                EndgameHunt.hitsGround(UnitType.Zerg_Mutalisk), EndgameHunt.hitsAir(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void liftedBuildingIsNotAHuntTargetForGroundOnlyAttackers() {
        for (UnitType ground : new UnitType[] {UnitType.Zerg_Zergling, UnitType.Zerg_Ultralisk, UnitType.Zerg_Lurker}) {
            assertFalse(EndgameHunt.isHuntTarget(UnitType.Terran_Barracks, true,
                    EndgameHunt.hitsGround(ground), EndgameHunt.hitsAir(ground)), ground.toString());
        }
    }

    @Test
    void mobileUnitsAreNotHuntTargets() {
        assertFalse(EndgameHunt.isHuntTarget(UnitType.Terran_SCV, false, true, true));
    }

    @Test
    void groundAttackerHuntsTheNearestGroundedBuildingPastANearerLiftedOne() {
        Position target = EndgameHunt.closestTarget(SQUAD, Arrays.asList(
                new EndgameHunt.Target(UnitType.Terran_Barracks, NEAR, true),
                new EndgameHunt.Target(UnitType.Terran_Refinery, FAR, false)), true, false);

        assertEquals(FAR, target);
    }

    @Test
    void groundAttackerStillHeadsForALiftedBuildingWhenNothingElseIsKnown() {
        Position target = EndgameHunt.closestTarget(SQUAD, Arrays.asList(
                new EndgameHunt.Target(UnitType.Terran_Barracks, FAR, true),
                new EndgameHunt.Target(UnitType.Terran_Engineering_Bay, NEAR, true)), true, false);

        assertEquals(NEAR, target);
    }

    @Test
    void antiAirAttackerHuntsTheNearestBuilding() {
        Position target = EndgameHunt.closestTarget(SQUAD, Arrays.asList(
                new EndgameHunt.Target(UnitType.Terran_Barracks, NEAR, true),
                new EndgameHunt.Target(UnitType.Terran_Refinery, FAR, false)), true, true);

        assertEquals(NEAR, target);
    }

    @Test
    void noKnownBuildingGivesNoHuntPosition() {
        assertNull(EndgameHunt.closestTarget(SQUAD, Collections.emptyList(), true, true));
    }

    @Test
    void onlyFlyingBuildingsRemainOnceTheEnemyHasNoUnitAndEveryBuildingIsLifted() {
        assertTrue(EndgameHunt.onlyFlyingBuildingsRemain(0, 2, 0));
        assertFalse(EndgameHunt.onlyFlyingBuildingsRemain(1, 2, 0));
        assertFalse(EndgameHunt.onlyFlyingBuildingsRemain(0, 2, 1));
        assertFalse(EndgameHunt.onlyFlyingBuildingsRemain(0, 0, 0));
    }

    @Test
    void squadWithNoAntiAirRequestsAntiAirWhenOnlyFlyingBuildingsRemain() {
        boolean onlyFlying = EndgameHunt.onlyFlyingBuildingsRemain(0, 2, 0);

        assertEquals(EndgameHunt.AntiAirRequest.HYDRALISK,
                EndgameHunt.antiAirRequest(onlyFlying, 0, false, true, false));
        assertEquals(EndgameHunt.AntiAirRequest.MUTALISK,
                EndgameHunt.antiAirRequest(onlyFlying, 0, true, true, false));
        assertEquals(EndgameHunt.AntiAirRequest.HYDRALISK_DEN,
                EndgameHunt.antiAirRequest(onlyFlying, 0, false, false, true));
    }

    @Test
    void antiAirIsNotRequestedWhileGroundedBuildingsOrEnoughAntiAirRemain() {
        assertEquals(EndgameHunt.AntiAirRequest.NONE,
                EndgameHunt.antiAirRequest(EndgameHunt.onlyFlyingBuildingsRemain(0, 2, 1), 0, true, true, true));
        assertEquals(EndgameHunt.AntiAirRequest.NONE,
                EndgameHunt.antiAirRequest(true, EndgameHunt.ANTI_AIR_TARGET, true, true, true));
        assertEquals(EndgameHunt.AntiAirRequest.HYDRALISK,
                EndgameHunt.antiAirRequest(true, EndgameHunt.ANTI_AIR_TARGET - 1, false, true, false));
        assertEquals(EndgameHunt.AntiAirRequest.NONE,
                EndgameHunt.antiAirRequest(true, 0, false, false, false));
    }

    @Test
    void flyingBuildingHuntWaitsForTheGameTimeAndSupplyFloors() {
        int late = EndgameHunt.MIN_ANTI_AIR_TIME.getFrames();
        int ahead = EndgameHunt.MIN_OUR_SUPPLY_USED;

        assertTrue(EndgameHunt.huntsFlyingBuildings(true, late, ahead));
        assertFalse(EndgameHunt.huntsFlyingBuildings(false, late, ahead));
        assertFalse(EndgameHunt.huntsFlyingBuildings(true, late - 1, ahead));
        assertFalse(EndgameHunt.huntsFlyingBuildings(true, late, ahead - 1));
    }

    @Test
    void everyAntiAirUnitFiresOnFlyers() {
        for (UnitType type : EndgameHunt.ANTI_AIR_UNITS) {
            assertTrue(EndgameHunt.hitsAir(type), type.toString());
        }
    }
}
