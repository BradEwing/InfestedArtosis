package info.tracking;

import bwapi.Position;
import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.TileFootprint;
import util.Time;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the null filter every position query in ObservedUnitTracker runs through, and the completion stamp a
 * type change drops. Standing up the state that produces a null - a living building whose location
 * clearLastKnownLocationsAt() has cleared - needs a live bwapi Unit, and the project has no mocking framework,
 * so the filter is exercised directly and tracked units come from ObservedUnitFixture.
 */
class ObservedUnitTrackerTest {

    private static final Position KNOWN = new Position(320, 640);
    private static final Time DRONE_OBSERVED = new Time(1400);
    private static final Time DRONE_COMPLETED = new Time(1496);
    private static final Time POOL_COMPLETED = new Time(2801);
    private static final Time WINDOW = new Time(1, 52);
    private static final Time MORPH_OBSERVED = new Time(1600);
    private static final Time WALL_CUTOFF = new Time(6, 0);

    /**
     * A Barracks centre on its build tiles, (8, 19) to (11, 21).
     */
    private static final Position GROUNDED = new Position(320, 656);

    @Test
    void recentWorkersOfAnyRaceAreFoundInsideTheArea() {
        for (UnitType worker : new UnitType[] {UnitType.Protoss_Probe, UnitType.Terran_SCV, UnitType.Zerg_Drone}) {
            ObservedUnit observed = ObservedUnitFixture.observedUnit(worker, new Time(5000));
            observed.setLastKnownLocation(KNOWN);
            ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(observed);

            Set<Position> positions = tracker.getRecentWorkerPositionsIn(tile -> true, 5100, 240);

            assertEquals(1, positions.size(), worker.toString());
            assertTrue(positions.contains(KNOWN));
        }
    }

    @Test
    void recentWorkerQueryLeavesOutStaleWorkersOtherAreasAndNonWorkers() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, new Time(5000));
        probe.setLastKnownLocation(KNOWN);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(probe);

        assertTrue(tracker.getRecentWorkerPositionsIn(tile -> true, 5241, 240).isEmpty());
        assertEquals(1, tracker.getRecentWorkerPositionsIn(tile -> true, 5240, 240).size());
        assertTrue(tracker.getRecentWorkerPositionsIn(tile -> false, 5100, 240).isEmpty());

        ObservedUnit zealot = ObservedUnitFixture.observedUnit(UnitType.Protoss_Zealot, new Time(5000));
        zealot.setLastKnownLocation(KNOWN);
        assertTrue(ObservedUnitFixture.trackerHolding(zealot)
                .getRecentWorkerPositionsIn(tile -> true, 5100, 240).isEmpty());
    }

    @Test
    void aWorkerWhosePositionWasClearedIsLeftOut() {
        ObservedUnit probe = ObservedUnitFixture.observedUnit(UnitType.Protoss_Probe, new Time(5000));

        assertTrue(ObservedUnitFixture.trackerHolding(probe)
                .getRecentWorkerPositionsIn(tile -> true, 5100, 240).isEmpty());
    }

    @Test
    void clearedLocationIsDroppedRatherThanReturnedAsNull() {
        Set<Position> positions = ObservedUnitTracker.knownPositions(Stream.of(KNOWN, null));

        assertFalse(positions.contains(null));
        assertEquals(1, positions.size());
        assertTrue(positions.contains(KNOWN));
    }

    @Test
    void allLocationsClearedProducesAnEmptySet() {
        assertTrue(ObservedUnitTracker.knownPositions(Stream.of(null, null)).isEmpty());
    }

    @Test
    void typeChangeDropsTheCompletionStamp() {
        ObservedUnit morphed = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED);

        assertEquals(UnitType.Zerg_Spawning_Pool, morphed.getUnitType());
        assertFalse(morphed.isCompleted());
        assertNull(morphed.getCompletedFrame());
    }

    @Test
    void morphedUnitIsStampedAtTheFrameItIsNextObservedComplete() {
        ObservedUnit morphed = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED);
        morphed.markCompleted(POOL_COMPLETED);

        assertTrue(morphed.isCompleted());
        assertEquals(POOL_COMPLETED, morphed.getCompletedFrame());
    }

    @Test
    void unchangedTypeKeepsTheCompletionStamp() {
        ObservedUnit drone = completedDrone();

        ObservedUnitTracker.updateUnitTypeChange(drone, UnitType.Zerg_Drone, MORPH_OBSERVED);

        assertTrue(drone.isCompleted());
        assertEquals(DRONE_COMPLETED, drone.getCompletedFrame());
    }

    @Test
    void completedCountIgnoresAUnitThatHasSinceChangedType() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        assertEquals(1, tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Drone, WINDOW));

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED);

        assertEquals(0, tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Spawning_Pool, WINDOW));
    }

    @Test
    void hatcheryThatMorphedIntoALairIsObservedAsLairTech() {
        ObservedUnit hatchery = ObservedUnitFixture.observedUnit(UnitType.Zerg_Hatchery, DRONE_OBSERVED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(hatchery);

        assertFalse(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair, UnitType.Zerg_Spire));

        ObservedUnitFixture.changeType(hatchery, UnitType.Zerg_Lair);

        assertTrue(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void hatcheryRelabelledAsALairAfterTheCutoffIsNotLairTechByTheCutoff() {
        ObservedUnit hatchery = ObservedUnitFixture.observedUnit(UnitType.Zerg_Hatchery, new Time(2, 30));
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(hatchery);
        ObservedUnitFixture.changeType(hatchery, UnitType.Zerg_Lair, new Time(8, 30));

        assertFalse(tracker.hasObservedAnyAsTypeBy(WALL_CUTOFF, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
        assertTrue(tracker.hasObservedAnyAsTypeBy(new Time(8, 30), UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void hatcheryRelabelledAsALairBeforeTheCutoffIsLairTechByTheCutoff() {
        ObservedUnit hatchery = ObservedUnitFixture.observedUnit(UnitType.Zerg_Hatchery, new Time(2, 30));
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(hatchery);
        ObservedUnitFixture.changeType(hatchery, UnitType.Zerg_Lair, new Time(3, 35));

        assertTrue(tracker.hasObservedAnyAsTypeBy(WALL_CUTOFF, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void lairTechObservedExactlyAtTheCutoffCounts() {
        ObservedUnit spire = ObservedUnitFixture.observedUnit(UnitType.Zerg_Spire, WALL_CUTOFF);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(spire);

        assertTrue(tracker.hasObservedAnyAsTypeBy(WALL_CUTOFF, UnitType.Zerg_Spire));
        assertFalse(tracker.hasObservedAnyAsTypeBy(new Time(5, 59), UnitType.Zerg_Spire));
    }

    @Test
    void destroyedUnitStillCountsAsObserved() {
        ObservedUnit spire = ObservedUnitFixture.observedUnit(UnitType.Zerg_Spire, DRONE_OBSERVED);
        spire.setDestroyedFrame(POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(spire);

        assertTrue(tracker.hasObservedAnyBeforeTime(POOL_COMPLETED, UnitType.Zerg_Lair, UnitType.Zerg_Spire));
    }

    @Test
    void aDestroyedUnitTheFilterAcceptsHasBeenObserved() {
        ObservedUnit corsair = ObservedUnitFixture.observedUnit(UnitType.Protoss_Corsair, DRONE_OBSERVED);
        corsair.setDestroyedFrame(POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(corsair);

        assertTrue(tracker.hasObservedAny(type -> type == UnitType.Protoss_Corsair));
        assertFalse(tracker.hasObservedAny(type -> type == UnitType.Protoss_Scout));
    }

    @Test
    void unitFirstObservedAfterTheTimeIsNotCounted() {
        ObservedUnit lair = ObservedUnitFixture.observedUnit(UnitType.Zerg_Lair, POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(lair);

        assertFalse(tracker.hasObservedAnyBeforeTime(WINDOW, UnitType.Zerg_Lair));
    }

    @Test
    void droneSeenEarlyIsObservedAsAPoolOnlyFromItsMorph() {
        ObservedUnit morphed = completedDrone();
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(morphed);

        ObservedUnitTracker.updateUnitTypeChange(morphed, UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED);

        assertEquals(DRONE_OBSERVED, morphed.getFirstObservedFrame());
        assertEquals(MORPH_OBSERVED, morphed.getTypeObservedFrame());
        assertFalse(tracker.hasObservedAsTypeBy(UnitType.Zerg_Spawning_Pool, DRONE_OBSERVED));
        assertTrue(tracker.hasObservedAsTypeBy(UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED));
    }

    @Test
    void poolLastSeenMorphingCountsAsIncompleteSinceThatFrame() {
        ObservedUnit pool = ObservedUnitFixture.observedUnit(UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED);
        pool.setLastObservedFrame(POOL_COMPLETED);
        ObservedUnitTracker tracker = ObservedUnitFixture.trackerHolding(pool);

        assertTrue(tracker.hasObservedIncompleteSince(UnitType.Zerg_Spawning_Pool, POOL_COMPLETED));
        assertFalse(tracker.hasObservedIncompleteSince(UnitType.Zerg_Spawning_Pool,
                new Time(POOL_COMPLETED.getFrames() + 1)));

        pool.markCompleted(POOL_COMPLETED);

        assertFalse(tracker.hasObservedIncompleteSince(UnitType.Zerg_Spawning_Pool, MORPH_OBSERVED));
    }

    @Test
    void assimilatorRebuiltOnItsGeyserIsTrackedAsLivingAgain() {
        Unit geyser = new TestUnits().unit(UnitType.Protoss_Assimilator, 61);
        ObservedUnitTracker tracker = new ObservedUnitTracker();

        tracker.onUnitShow(geyser, 6921, false);
        tracker.onUnitDestroy(geyser, 8318);
        assertTrue(tracker.getLivingObservedUnits().isEmpty());

        tracker.onUnitShow(geyser, 18874, false);

        assertEquals(1, tracker.getLivingObservedUnits().size());
        assertEquals(1, tracker.getCountOfLivingUnits(UnitType.Protoss_Assimilator));
    }

    private static ObservedUnit completedDrone() {
        ObservedUnit drone = ObservedUnitFixture.observedUnit(UnitType.Zerg_Drone, DRONE_OBSERVED);
        drone.markCompleted(DRONE_COMPLETED);
        return drone;
    }

    @Test
    void aFootprintIsNeverPairedWithItself() {
        List<TileFootprint> one = Collections.singletonList(barracksAt(10, 10));

        assertFalse(ObservedUnitTracker.hasPairWithinTileGap(one, isBarracks(), isBarracks(), 1, (a, b) -> true));
    }

    @Test
    void aPairIsFoundOnlyWithinTheGapAndWithThePairFilter() {
        TileFootprint barracks = barracksAt(10, 10);
        TileFootprint depot = new TileFootprint(UnitType.Terran_Supply_Depot, new TilePosition(15, 10));
        List<TileFootprint> pair = Arrays.asList(depot, barracks);

        assertTrue(ObservedUnitTracker.hasPairWithinTileGap(pair, isBarracks(),
                type -> type == UnitType.Terran_Supply_Depot, 1, (first, second) -> first == barracks));
        assertFalse(ObservedUnitTracker.hasPairWithinTileGap(pair, isBarracks(),
                type -> type == UnitType.Terran_Supply_Depot, 0, (first, second) -> true));
        assertFalse(ObservedUnitTracker.hasPairWithinTileGap(pair, isBarracks(),
                type -> type == UnitType.Terran_Supply_Depot, 1, (first, second) -> false));
    }

    @Test
    void aBarracksSeenGroundedOnItsBuildTilesHasItsFootprintThere() {
        ObservedUnit barracks = groundedBarracks();

        assertTrue(barracks.isGroundedAtAnchor());
        assertFootprintAt(GROUNDED, footprints(barracks));
    }

    @Test
    void aDestroyedBuildingHasNoFootprint() {
        ObservedUnit barracks = groundedBarracks();
        barracks.setDestroyedFrame(DRONE_COMPLETED);

        assertTrue(footprints(barracks).isEmpty());
    }

    @Test
    void wallPartnersSeenAtRealGrimHammerPositionsAreAnchored() {
        ObservedUnit bunker = ObservedUnitFixture.observedUnit(UnitType.Terran_Bunker, new Position(3440, 864),
                DRONE_OBSERVED);
        bunker.recordLift(false);
        ObservedUnit depot = ObservedUnitFixture.observedUnit(UnitType.Terran_Supply_Depot, new Position(3344, 864),
                DRONE_OBSERVED);
        depot.recordLift(false);

        assertTrue(bunker.isGroundedAtAnchor());
        assertEquals(new TilePosition(106, 26), footprints(bunker).get(0).getTopLeft());
        assertTrue(depot.isGroundedAtAnchor());
        assertEquals(new TilePosition(103, 26), footprints(depot).get(0).getTopLeft());
    }

    @Test
    void aBuildingOfAnotherTypeHasNoFootprint() {
        ObservedUnit depot = ObservedUnitFixture.observedUnit(UnitType.Terran_Supply_Depot, new Position(336, 640),
                DRONE_OBSERVED);
        depot.recordLift(false);

        assertTrue(depot.isGroundedAtAnchor());
        assertTrue(ObservedUnitFixture.trackerHolding(depot).getGroundedFootprints(isBarracks(), WALL_CUTOFF)
                .isEmpty());
    }

    @Test
    void aBuildingFirstObservedAfterTheCutoffHasNoFootprint() {
        ObservedUnit barracks = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, GROUNDED,
                new Time(WALL_CUTOFF.getFrames() + 1));
        barracks.recordLift(false);

        assertTrue(footprints(barracks).isEmpty());
    }

    @Test
    void aBarracksLandedOnOtherTilesHasNoFootprint() {
        ObservedUnit barracks = groundedBarracks();
        barracks.setLastKnownLocation(new Position(GROUNDED.getX() - 96, GROUNDED.getY() + 864));
        barracks.recordLift(false);

        assertFalse(barracks.isGroundedAtAnchor());
        assertTrue(footprints(barracks).isEmpty());
    }

    @Test
    void aBarracksLastSeenLiftedHasNoFootprint() {
        ObservedUnit barracks = groundedBarracks();
        barracks.setLastKnownLocation(new Position(GROUNDED.getX() - 1, GROUNDED.getY() - 43));
        barracks.recordLift(true);

        assertFalse(barracks.isGroundedAtAnchor());
        assertTrue(footprints(barracks).isEmpty());
    }

    @Test
    void aGateBarracksLiftedAndLandedBackInPlaceHasItsFootprintAgain() {
        ObservedUnit barracks = groundedBarracks();
        barracks.setLastKnownLocation(new Position(GROUNDED.getX() - 1, GROUNDED.getY() - 43));
        barracks.recordLift(true);
        barracks.setLastKnownLocation(GROUNDED);
        barracks.recordLift(false);

        assertTrue(barracks.isGroundedAtAnchor());
        assertFootprintAt(GROUNDED, footprints(barracks));
    }

    @Test
    void aBarracksFirstSeenLiftedIsAnchoredWhereItLands() {
        Position landed = new Position(3392, 944);
        ObservedUnit barracks = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(3391, 901),
                DRONE_OBSERVED);
        barracks.recordLift(true);

        assertTrue(footprints(barracks).isEmpty());

        barracks.setLastKnownLocation(landed);
        barracks.recordLift(false);

        assertEquals(landed, barracks.getGroundedAnchor());
        assertFootprintAt(landed, footprints(barracks));
    }

    @Test
    void aBarracksFirstSeenOffItsBuildTileCentreIsAnchoredWhereItSettles() {
        Position settled = new Position(3584, 2480);
        ObservedUnit barracks = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, new Position(3584, 2479),
                DRONE_OBSERVED);
        barracks.recordLift(false);

        assertNull(barracks.getGroundedAnchor());
        assertTrue(footprints(barracks).isEmpty());

        barracks.setLastKnownLocation(settled);
        barracks.recordLift(false);

        assertFootprintAt(settled, footprints(barracks));
    }

    @Test
    void aBuildingWhosePositionWasRuledOutHasNoFootprint() {
        ObservedUnit barracks = groundedBarracks();
        barracks.setLastKnownLocation(null);

        assertFalse(barracks.isGroundedAtAnchor());
        assertTrue(footprints(barracks).isEmpty());
    }

    private static ObservedUnit groundedBarracks() {
        ObservedUnit barracks = ObservedUnitFixture.observedUnit(UnitType.Terran_Barracks, GROUNDED, DRONE_OBSERVED);
        barracks.recordLift(false);
        return barracks;
    }

    private static void assertFootprintAt(Position centre, List<TileFootprint> footprints) {
        assertEquals(1, footprints.size());
        assertEquals(TileFootprint.centredAt(UnitType.Terran_Barracks, centre).getTopLeft(),
                footprints.get(0).getTopLeft());
    }

    private static List<TileFootprint> footprints(ObservedUnit observedUnit) {
        return ObservedUnitFixture.trackerHolding(observedUnit).getGroundedFootprints(type -> true, WALL_CUTOFF);
    }

    private static TileFootprint barracksAt(int left, int top) {
        return new TileFootprint(UnitType.Terran_Barracks, new TilePosition(left, top));
    }

    private static Predicate<UnitType> isBarracks() {
        return type -> type == UnitType.Terran_Barracks;
    }
}
