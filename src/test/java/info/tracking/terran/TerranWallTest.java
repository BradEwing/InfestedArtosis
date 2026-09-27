package info.tracking.terran;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranWallTest {

    private static final String WALL = "1Base;TerranWallMain";

    private static final String NO_WALL = "1Base;2RaxAcademy";

    @Test
    void eitherWallNamedInTheRecordedStrategiesIsAWall() {
        assertTrue(TerranWall.isWallIn("TerranWallNatural"));
        assertTrue(TerranWall.isWallIn("1Base;TerranWallMain"));
        assertTrue(TerranWall.isWallIn("2RaxAcademy;TerranWallNatural;TerranWallMain"));
    }

    @Test
    void recordedStrategiesWithoutAWallAreNotAWall() {
        assertFalse(TerranWall.isWallIn(null));
        assertFalse(TerranWall.isWallIn(""));
        assertFalse(TerranWall.isWallIn("1Base;2RaxAcademy;SCVRush"));
        assertFalse(TerranWall.isWallIn("TerranWall"));
        assertFalse(TerranWall.isWallIn("TerranWallMainline"));
    }

    @Test
    void aWallPersistsWhenTwoOfTheLastThreeGamesDetectedIt() {
        assertTrue(TerranWall.isPersistent(Arrays.asList(WALL, WALL)));
        assertTrue(TerranWall.isPersistent(Arrays.asList(WALL, NO_WALL, WALL)));
        assertTrue(TerranWall.isPersistent(Arrays.asList(NO_WALL, NO_WALL, WALL, NO_WALL, WALL)));
        assertTrue(TerranWall.isPersistent(Arrays.asList(WALL, WALL, WALL)));
    }

    @Test
    void aWallInOneOfTheLastThreeGamesDoesNotPersist() {
        assertFalse(TerranWall.isPersistent(Collections.emptyList()));
        assertFalse(TerranWall.isPersistent(Collections.singletonList(WALL)));
        assertFalse(TerranWall.isPersistent(Arrays.asList(NO_WALL, WALL)));
        assertFalse(TerranWall.isPersistent(Arrays.asList(WALL, NO_WALL, NO_WALL, WALL)));
        assertFalse(TerranWall.isPersistent(Arrays.asList(WALL, WALL, NO_WALL, NO_WALL)));
        assertFalse(TerranWall.isPersistent(Arrays.asList(null, WALL, "")));
    }

    @Test
    void terranBuildingsAreReadForWalls() {
        assertTrue(TerranWall.isTerranBuilding(UnitType.Terran_Barracks));
        assertTrue(TerranWall.isTerranBuilding(UnitType.Terran_Supply_Depot));
        assertTrue(TerranWall.isTerranBuilding(UnitType.Terran_Bunker));
        assertTrue(TerranWall.isTerranBuilding(UnitType.Terran_Engineering_Bay));
    }

    @Test
    void otherRacesBuildingsAndTerranUnitsAreNotReadForWalls() {
        assertFalse(TerranWall.isTerranBuilding(UnitType.Protoss_Forge));
        assertFalse(TerranWall.isTerranBuilding(UnitType.Protoss_Photon_Cannon));
        assertFalse(TerranWall.isTerranBuilding(UnitType.Terran_Marine));
        assertFalse(TerranWall.isTerranBuilding(UnitType.Terran_SCV));
    }

    @Test
    void nothingFirstObservedAfterTheCutoffIsRead() {
        ObservedUnit late = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(640, 640),
                new Time(TerranWall.CUTOFF.getFrames() + 1));

        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(late)).isEmpty());
    }

    @Test
    void aLiftedOrMovedBarracksIsNeverPartOfAWall() {
        ObservedUnit moved = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(3680, 784),
                new Time(3, 0));
        moved.setLastKnownLocation(new Position(3599, 1638));
        ObservedUnit lifted = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(3680, 784),
                new Time(3, 0));
        lifted.setSeenLifted(true);

        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(moved)).isEmpty());
        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(lifted)).isEmpty());
    }
}
