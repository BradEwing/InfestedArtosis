package info.tracking.terran;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranWallTest {

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
    void theBarracksAndItsPartnersAreWallBuildings() {
        assertTrue(TerranWall.isWallBuilding(UnitType.Terran_Barracks));
        assertTrue(TerranWall.isWallBuilding(UnitType.Terran_Supply_Depot));
        assertTrue(TerranWall.isWallBuilding(UnitType.Terran_Bunker));
    }

    @Test
    void protossNaturalWallTypesAndOtherTerranBuildingsAreNotWallBuildings() {
        assertFalse(TerranWall.isWallBuilding(UnitType.Protoss_Forge));
        assertFalse(TerranWall.isWallBuilding(UnitType.Protoss_Photon_Cannon));
        assertFalse(TerranWall.isWallBuilding(UnitType.Terran_Engineering_Bay));
        assertFalse(TerranWall.isWallBuilding(UnitType.Terran_Command_Center));
    }
}
