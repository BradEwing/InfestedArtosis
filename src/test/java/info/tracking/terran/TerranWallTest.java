package info.tracking.terran;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitFixture;
import org.junit.jupiter.api.Test;
import util.TileFootprint;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        ObservedUnit late = grounded(new Position(640, 656), new Time(TerranWall.CUTOFF.getFrames() + 1));
        ObservedUnit onTime = grounded(new Position(640, 656), TerranWall.CUTOFF);

        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(late)).isEmpty());
        assertFalse(TerranWall.footprints(ObservedUnitFixture.trackerHolding(onTime)).isEmpty());
    }

    @Test
    void aFloatingBarracksIsNeverPartOfAWall() {
        ObservedUnit floating = grounded(new Position(3680, 784), new Time(3, 0));
        floating.setLastKnownLocation(new Position(3605, 1682));
        floating.recordLift(true);
        ObservedUnit landedElsewhere = grounded(new Position(3680, 784), new Time(3, 0));
        landedElsewhere.setLastKnownLocation(new Position(3616, 1680));
        landedElsewhere.recordLift(false);

        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(floating)).isEmpty());
        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(landedElsewhere)).isEmpty());
    }

    @Test
    void aGateBarracksFirstSeenLiftedIsReadOnceItLandsInItsWall() {
        ObservedUnit gate = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(3391, 901),
                new Time(3, 12));
        gate.recordLift(true);
        List<TileFootprint> depot = Collections.singletonList(
                new TileFootprint(UnitType.Terran_Supply_Depot, new TilePosition(108, 28)));

        assertTrue(TerranWall.footprints(ObservedUnitFixture.trackerHolding(gate)).isEmpty());

        gate.setLastKnownLocation(new Position(3392, 944));
        gate.recordLift(false);
        List<TileFootprint> wall = new ArrayList<>(TerranWall.footprints(ObservedUnitFixture.trackerHolding(gate)));
        wall.addAll(depot);

        assertTrue(TerranWall.hasPair(wall, (barracks, partner) -> true));
    }

    private static ObservedUnit grounded(Position position, Time firstObserved) {
        ObservedUnit barracks = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, position, firstObserved);
        barracks.recordLift(false);
        return barracks;
    }
}
