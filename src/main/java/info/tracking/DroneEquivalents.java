package info.tracking;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import lombok.Value;
import util.Time;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * How many Drones a Zerg opponent has produced, reconstructed from what we have seen: living Drones, plus every
 * standing structure a Drone became other than the start Hatchery, plus Drones seen to die. Every Zerg structure
 * consumes the Drone that builds it, so each counts as one Drone whatever it has since morphed into: a Sunken
 * Colony counts once whether or not its Creep Colony was seen, and a Lair counts once unless it is the start
 * depot. A morph keeps the same Unit, so the tracker holds one entry per building and a Creep Colony later seen
 * as a Sunken is never counted twice.
 * <p>
 * An Extractor is the exception to the kept Unit: its Drone is destroyed on the frame the geyser becomes the
 * Extractor. A Drone destroyed within {@link #EXTRACTOR_CONSUMED_FRAMES} of an Extractor first being observed is
 * read as that Extractor's builder rather than as a loss, including an Extractor since cancelled. A cancelled or
 * destroyed Extractor no longer counts as a structure.
 * <p>
 * Everything unseen is missing, so the total is a lower bound: Drones inside an Extractor, a scouting Drone and
 * Drones still in their Eggs are not counted.
 */
@Value
public class DroneEquivalents {

    static final int EXTRACTOR_CONSUMED_FRAMES = 24;

    private static final Set<UnitType> DRONE_BUILT_STRUCTURES = EnumSet.of(
            UnitType.Zerg_Hatchery,
            UnitType.Zerg_Lair,
            UnitType.Zerg_Hive,
            UnitType.Zerg_Creep_Colony,
            UnitType.Zerg_Sunken_Colony,
            UnitType.Zerg_Spore_Colony,
            UnitType.Zerg_Extractor,
            UnitType.Zerg_Spawning_Pool,
            UnitType.Zerg_Evolution_Chamber,
            UnitType.Zerg_Hydralisk_Den,
            UnitType.Zerg_Spire,
            UnitType.Zerg_Greater_Spire,
            UnitType.Zerg_Queens_Nest,
            UnitType.Zerg_Ultralisk_Cavern,
            UnitType.Zerg_Defiler_Mound
    );

    private static final Set<UnitType> DEPOTS = EnumSet.of(
            UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);

    int drones;
    int structures;
    int lostDrones;

    public int total() {
        return drones + structures + lostDrones;
    }

    /**
     * Counts the drone equivalents among the tracked units. A structure whose position is no longer known has
     * been seen to be gone and does not count.
     *
     * @param units every tracked enemy unit, living and destroyed
     * @param extractorsObserved the frame each enemy Extractor was first observed, including Extractors since
     *     cancelled or destroyed
     * @param isStartDepot whether a depot standing on this tile is the enemy's start Hatchery
     */
    static DroneEquivalents of(Collection<ObservedUnit> units, Collection<Time> extractorsObserved,
                               Predicate<TilePosition> isStartDepot) {
        int drones = 0;
        int structures = 0;
        for (ObservedUnit ou : units) {
            if (ou.getDestroyedFrame() != null) {
                continue;
            }
            if (ou.getUnitType() == UnitType.Zerg_Drone) {
                drones++;
            } else if (isDroneBuiltStructure(ou, isStartDepot)) {
                structures++;
            }
        }
        return new DroneEquivalents(drones, structures, lostDrones(units, extractorsObserved));
    }

    private static boolean isDroneBuiltStructure(ObservedUnit ou, Predicate<TilePosition> isStartDepot) {
        if (!DRONE_BUILT_STRUCTURES.contains(ou.getUnitType())) {
            return false;
        }
        Position position = ou.getLastKnownLocation();
        if (position == null) {
            return false;
        }
        return !DEPOTS.contains(ou.getUnitType()) || !isStartDepot.test(position.toTilePosition());
    }

    private static int lostDrones(Collection<ObservedUnit> units, Collection<Time> extractorsObserved) {
        return (int) units.stream()
                .filter(ou -> ou.getUnitType() == UnitType.Zerg_Drone)
                .filter(ou -> ou.getDestroyedFrame() != null)
                .filter(ou -> !becameAnExtractor(ou.getDestroyedFrame(), extractorsObserved))
                .count();
    }

    static boolean becameAnExtractor(Time destroyedFrame, Collection<Time> extractorsObserved) {
        return extractorsObserved.stream()
                .anyMatch(observed -> Math.abs(observed.getFrames() - destroyedFrame.getFrames())
                        <= EXTRACTOR_CONSUMED_FRAMES);
    }
}
