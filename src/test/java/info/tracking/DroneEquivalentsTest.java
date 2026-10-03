package info.tracking;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneEquivalentsTest {

    private static final Position START_DEPOT = new Position(3744, 208);
    private static final Position IN_MAIN = new Position(3552, 400);
    private static final Position NATURAL = new Position(3200, 1200);
    private static final Predicate<TilePosition> IS_START_DEPOT = tile -> tile.equals(START_DEPOT.toTilePosition());
    private static final Time SCOUTED = new Time(2500);
    private static final Time EXTRACTOR_STARTED = new Time(2025);
    private static final Time EXTRACTOR_CANCELLED = new Time(2300);

    @Test
    void dronesAndDroneBuiltStructuresEachCountOne() {
        List<ObservedUnit> units = drones(8);
        units.add(structure(UnitType.Zerg_Spawning_Pool, IN_MAIN));
        units.add(structure(UnitType.Zerg_Extractor, IN_MAIN));

        DroneEquivalents equivalents = of(units);

        assertEquals(8, equivalents.getDrones());
        assertEquals(2, equivalents.getStructures());
        assertEquals(10, equivalents.total());
    }

    @Test
    void startHatcheryIsNotCountedButASecondHatcheryIs() {
        List<ObservedUnit> units = drones(8);
        units.add(structure(UnitType.Zerg_Hatchery, START_DEPOT));
        units.add(structure(UnitType.Zerg_Hatchery, IN_MAIN));
        units.add(structure(UnitType.Zerg_Hatchery, NATURAL));

        assertEquals(10, of(units).total());
    }

    @Test
    void startLairIsNotCountedButAnInMainLairIs() {
        List<ObservedUnit> units = new ArrayList<>();
        units.add(structure(UnitType.Zerg_Lair, START_DEPOT));
        assertEquals(0, of(units).total());

        units.add(structure(UnitType.Zerg_Lair, IN_MAIN));
        assertEquals(1, of(units).total());
    }

    @Test
    void nonDroneUnitsDoNotCount() {
        List<ObservedUnit> units = new ArrayList<>();
        UnitType[] nonDrones = {UnitType.Zerg_Zergling, UnitType.Zerg_Overlord, UnitType.Zerg_Larva, UnitType.Zerg_Egg};
        for (UnitType type : nonDrones) {
            units.add(ObservedUnitFixture.observedUnit(type, IN_MAIN, SCOUTED));
        }

        assertEquals(0, of(units).total());
    }

    @Test
    void cancelledExtractorDoesNotCount() {
        List<ObservedUnit> units = drones(8);
        ObservedUnit extractor = structure(UnitType.Zerg_Extractor, IN_MAIN);
        extractor.setDestroyedFrame(EXTRACTOR_CANCELLED);
        units.add(extractor);

        assertEquals(8, of(units).total());
    }

    @Test
    void droneSeenBecomingAnExtractorIsNotALoss() {
        List<ObservedUnit> units = drones(7);
        ObservedUnit builder = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, IN_MAIN, new Time(1500));
        builder.setDestroyedFrame(EXTRACTOR_STARTED);
        units.add(builder);
        units.add(ObservedUnitFixture.observedUnit(UnitType.Zerg_Extractor, IN_MAIN, EXTRACTOR_STARTED));

        DroneEquivalents equivalents = DroneEquivalents.of(units,
                Collections.singletonList(EXTRACTOR_STARTED), IS_START_DEPOT);

        assertEquals(0, equivalents.getLostDrones());
        assertEquals(8, equivalents.total());
    }

    @Test
    void builderOfACancelledExtractorIsNotALoss() {
        List<ObservedUnit> units = drones(8);
        ObservedUnit builder = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, IN_MAIN, new Time(1500));
        builder.setDestroyedFrame(EXTRACTOR_STARTED);
        units.add(builder);

        DroneEquivalents equivalents = DroneEquivalents.of(units,
                Collections.singletonList(EXTRACTOR_STARTED), IS_START_DEPOT);

        assertEquals(8, equivalents.total());
    }

    @Test
    void droneKilledAwayFromAnyExtractorCounts() {
        List<ObservedUnit> units = drones(8);
        ObservedUnit killed = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, IN_MAIN, new Time(1500));
        killed.setDestroyedFrame(new Time(2600));
        units.add(killed);

        DroneEquivalents equivalents = DroneEquivalents.of(units,
                Collections.singletonList(EXTRACTOR_STARTED), IS_START_DEPOT);

        assertEquals(1, equivalents.getLostDrones());
        assertEquals(9, equivalents.total());
    }

    @Test
    void extractorWindowIsBounded() {
        List<Time> extractors = Collections.singletonList(EXTRACTOR_STARTED);
        int window = DroneEquivalents.EXTRACTOR_CONSUMED_FRAMES;

        assertTrue(DroneEquivalents.becameAnExtractor(
                new Time(EXTRACTOR_STARTED.getFrames() + window), extractors));
        assertFalse(DroneEquivalents.becameAnExtractor(
                new Time(EXTRACTOR_STARTED.getFrames() + window + 1), extractors));
    }

    @Test
    void droneSeenMorphingIntoAPoolCountsOnceAsAStructure() {
        List<ObservedUnit> units = drones(8);
        ObservedUnit morphed = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, IN_MAIN, new Time(1400));
        units.add(morphed);
        assertEquals(9, of(units).getDrones());

        ObservedUnitFixture.changeType(morphed, UnitType.Zerg_Spawning_Pool, new Time(1611));
        DroneEquivalents equivalents = of(units);

        assertEquals(8, equivalents.getDrones());
        assertEquals(1, equivalents.getStructures());
        assertEquals(9, equivalents.total());
    }

    @Test
    void sunkenSeenWithoutItsCreepColonyCountsOne() {
        List<ObservedUnit> units = new ArrayList<>();
        units.add(structure(UnitType.Zerg_Sunken_Colony, IN_MAIN));

        assertEquals(1, of(units).total());
    }

    @Test
    void creepColonyLaterSeenAsASunkenStillCountsOne() {
        List<ObservedUnit> units = new ArrayList<>();
        ObservedUnit colony = structure(UnitType.Zerg_Creep_Colony, IN_MAIN);
        units.add(colony);
        assertEquals(1, of(units).total());

        ObservedUnitFixture.changeType(colony, UnitType.Zerg_Sunken_Colony, new Time(3000));

        assertEquals(1, of(units).total());
    }

    @Test
    void structureSeenToBeGoneDoesNotCount() {
        List<ObservedUnit> units = new ArrayList<>();
        units.add(ObservedUnitFixture.observedUnit(UnitType.Zerg_Spawning_Pool, SCOUTED));

        assertEquals(0, of(units).total());
    }

    private static DroneEquivalents of(List<ObservedUnit> units) {
        return DroneEquivalents.of(units, Collections.emptyList(), IS_START_DEPOT);
    }

    private static ObservedUnit structure(UnitType type, Position position) {
        return ObservedUnitFixture.observedUnit(type, position, SCOUTED);
    }

    private static List<ObservedUnit> drones(int count) {
        ObservedUnit[] drones = new ObservedUnit[count];
        for (int i = 0; i < count; i++) {
            drones[i] = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, IN_MAIN, SCOUTED);
        }
        return new ArrayList<>(Arrays.asList(drones));
    }
}
