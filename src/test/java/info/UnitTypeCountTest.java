package info;

import bwapi.UnitType;
import macro.HatcheryCapacity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnitTypeCountTest {

    private static int depotCount(UnitTypeCount count) {
        return count.get(UnitType.Zerg_Hatchery)
                + count.get(UnitType.Zerg_Lair)
                + count.get(UnitType.Zerg_Hive);
    }

    private static UnitTypeCount withHatchery() {
        UnitTypeCount count = new UnitTypeCount();
        count.addUnit(UnitType.Zerg_Hatchery);
        return count;
    }

    @Test
    void depotCountSurvivesLairMorphAssignment() {
        UnitTypeCount count = withHatchery();

        count.startBuildingMorph(UnitType.Zerg_Lair);

        assertEquals(1, depotCount(count));
        assertEquals(0, count.get(UnitType.Zerg_Drone));
    }

    @Test
    void depotCountSurvivesLairMorphCompletion() {
        UnitTypeCount count = withHatchery();

        count.startBuildingMorph(UnitType.Zerg_Lair);
        count.addUnit(UnitType.Zerg_Lair);
        count.completeMorph(UnitType.Zerg_Lair);

        assertEquals(1, depotCount(count));
        assertEquals(0, count.get(UnitType.Zerg_Hatchery));
        assertEquals(1, count.get(UnitType.Zerg_Lair));
    }

    @Test
    void depotCountSurvivesHiveMorph() {
        UnitTypeCount count = new UnitTypeCount();
        count.addUnit(UnitType.Zerg_Lair);

        count.startBuildingMorph(UnitType.Zerg_Hive);
        assertEquals(1, depotCount(count));
        assertEquals(0, count.get(UnitType.Zerg_Drone));

        count.addUnit(UnitType.Zerg_Hive);
        count.completeMorph(UnitType.Zerg_Hive);

        assertEquals(1, depotCount(count));
        assertEquals(0, count.get(UnitType.Zerg_Lair));
        assertEquals(1, count.get(UnitType.Zerg_Hive));
    }

    @Test
    void greaterSpireMorphKeepsSpireCountedUntilComplete() {
        UnitTypeCount count = new UnitTypeCount();
        count.addUnit(UnitType.Zerg_Spire);

        count.startBuildingMorph(UnitType.Zerg_Greater_Spire);
        assertEquals(1, count.get(UnitType.Zerg_Spire));
        assertEquals(0, count.get(UnitType.Zerg_Drone));

        count.addUnit(UnitType.Zerg_Greater_Spire);
        count.completeMorph(UnitType.Zerg_Greater_Spire);

        assertEquals(0, count.get(UnitType.Zerg_Spire));
        assertEquals(1, count.get(UnitType.Zerg_Greater_Spire));
    }

    @Test
    void lairDestroyedWhileMorphingClearsTheHatchery() {
        UnitTypeCount count = withHatchery();

        count.startBuildingMorph(UnitType.Zerg_Lair);
        count.removeDestroyedUnit(UnitType.Zerg_Lair, false);

        assertEquals(0, depotCount(count));
        assertEquals(0, count.get(UnitType.Zerg_Hatchery));
        assertEquals(0, count.get(UnitType.Zerg_Lair));
    }

    @Test
    void completedLairDestroyedClearsTheLair() {
        UnitTypeCount count = withHatchery();

        count.startBuildingMorph(UnitType.Zerg_Lair);
        count.addUnit(UnitType.Zerg_Lair);
        count.completeMorph(UnitType.Zerg_Lair);
        count.removeDestroyedUnit(UnitType.Zerg_Lair, true);

        assertEquals(0, depotCount(count));
    }

    @Test
    void sunkenMorphConsumesTheCreepColony() {
        UnitTypeCount count = new UnitTypeCount();
        count.addUnit(UnitType.Zerg_Creep_Colony);

        count.startBuildingMorph(UnitType.Zerg_Sunken_Colony);

        assertEquals(0, count.get(UnitType.Zerg_Creep_Colony));
        assertEquals(0, count.get(UnitType.Zerg_Drone));
    }

    @Test
    void droneBuiltStructureConsumesTheDrone() {
        UnitTypeCount count = new UnitTypeCount();
        count.addUnit(UnitType.Zerg_Drone);

        count.startBuildingMorph(UnitType.Zerg_Spawning_Pool);

        assertEquals(0, count.get(UnitType.Zerg_Drone));
    }

    @Test
    void droneBuiltStructuresHaveNoMorphPredecessor() {
        assertNull(UnitTypeCount.morphPredecessor(UnitType.Zerg_Hatchery));
        assertNull(UnitTypeCount.morphPredecessor(UnitType.Zerg_Sunken_Colony));
        assertNull(UnitTypeCount.morphPredecessor(UnitType.Zerg_Spire));
    }

    @Test
    void lairMorphDoesNotMakeUsLookBehindOnHatchery() {
        UnitTypeCount count = withHatchery();

        count.startBuildingMorph(UnitType.Zerg_Lair);

        assertFalse(HatcheryCapacity.isBehind(depotCount(count), 1, false, false));
    }
}
