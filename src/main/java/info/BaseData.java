package info;

import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import info.exception.NoWalkablePathException;
import info.map.GameMap;
import info.map.GroundPath;
import info.map.GroundPathComparator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects base and building state.
 *
 */
public class BaseData {

    private bwem.Base mainBase;

    private HashSet<Unit> macroHatcheries = new HashSet<>();
    private HashSet<Unit> baseHatcheries = new HashSet<>();

    private HashSet<Base> allBases = new HashSet<>();
    private HashSet<Base> myBases = new HashSet<>();
    private HashSet<Base> reservedBases = new HashSet<>();
    private HashSet<Base> enemyBases = new HashSet<>();
    private HashSet<Base> islands = new HashSet<>();
    private HashSet<Base> mineralOnlyBase = new HashSet<>();

    private HashMap<Unit, Base> baseLookup = new HashMap<>();
    private HashSet<TilePosition> baseTilePositionSet = new HashSet<>();

    private HashMap<Base, GroundPath> allBasePaths = new HashMap<>();
    private HashMap<Base, GroundPath> availableBases = new HashMap<>();

    public BaseData(List<Base> allBases) {
        for (Base base: allBases) {
            this.allBases.add(base);
            this.baseTilePositionSet.add(base.getLocation());
            if (base.getGeysers().size() == 0) {
                mineralOnlyBase.add(base);
            }
        }
    }

    public HashMap<Base, GroundPath> getBasePaths() {
        return this.allBasePaths;
    }

    public void initializeMainBase(Base base, GameMap map) {

        this.mainBase = base;

        HashSet<Base> potentialBases = allBases.stream()
                .filter(b -> b != base)
                .collect(Collectors.toCollection(HashSet::new));

        // TODO: Fix potentialBases
        for (Base b: potentialBases) {
            try {
                GroundPath path = map.aStarSearch(mainBase.getLocation(), b.getLocation());
                this.availableBases.put(b, path);
                this.allBasePaths.put(b, path);
            } catch (NoWalkablePathException e) {
                this.islands.add(b);
            }
        }
    }

    public void addBase(Unit hatchery, Base base) {
        baseHatcheries.add(hatchery);
        myBases.add(base);
        baseLookup.put(hatchery, base);
        availableBases.remove(base);
        reservedBases.remove(base);
    }

    public Base reserveBase() {
        final Base base = this.findNewBase();
        if (base == null) {
            return null;
        }
        reservedBases.add(base);
        return base;
    }

    public Base claimBase(Unit hatchery) {
        TilePosition tp = hatchery.getTilePosition();
        Base base = null;
        for (Base reservedBase: reservedBases) {
            if (tp.equals(reservedBase.getLocation())) {
                base = reservedBase;
                break;
            }
        }

        if (base == null) {
            base = this.findNewBase();
            if (base == null) {
                return null;
            }
        }

        return base;
    }

    public Base get(Unit hatchery) {
        return baseLookup.get(hatchery);
    }

    public boolean isBase(Unit hatchery) {
        return baseHatcheries.contains(hatchery);
    }

    public void addMacroHatchery(Unit hatchery) {
        macroHatcheries.add(hatchery);
    }

    public void removeHatchery(Unit hatchery) {
        if (myBases.contains(hatchery)) {
            removeBase(hatchery);
        } else {
            removeMacroHatchery(hatchery);
        }
    }


    private void removeBase(Unit hatchery) {
        Base base = baseLookup.get(hatchery);
        baseHatcheries.remove(hatchery);
        myBases.remove(base);

        GroundPath pathToRemovedBase = this.allBasePaths.get(base);
        this.availableBases.put(base, pathToRemovedBase);
    }

    private void removeMacroHatchery(Unit hatchery) {
        macroHatcheries.remove(hatchery);
    }

    public HashSet<Unit> baseHatcheries() { return baseHatcheries; }

    public int currentBaseCount() { return baseHatcheries.size(); }

    public int numHatcheries() { return myBases.size() + macroHatcheries.size() + reservedBases.size(); }

    public TilePosition mainBasePosition() { return mainBase.getLocation(); }

    public boolean isBaseTilePosition(TilePosition tilePosition) {
        return baseTilePositionSet.contains(tilePosition);
    }

    // TODO: Consider walking path, prioritize gas bases over mineral only
    public Base findRandomNewBase() {
        Base closestUnoccupiedBase = null;
        double closestDistance = Double.MAX_VALUE;
        for (Base b : allBases) {
            // TODO: Consider enemy bases
            if (myBases.contains(b)) {
                continue;
            }

            double distance = mainBase.getLocation().getDistance(b.getLocation());

            if (distance < closestDistance) {
                closestUnoccupiedBase = b;
                closestDistance = distance;
            }
        }

        return closestUnoccupiedBase;
    }

    /**
     * Finds a new base. Searches for the closest unclaimed base by ground distance.
     *
     * If this is the second base, mineral only bases will be excluded from consideration. Used to take the natural
     * correctly on maps like Andromeda.
     *
     * Returns null if no bases are available. A base is considered an island if the distance is infinitely far away.
     * @return
     */
    public Base findNewBase() {
        // Islands are not included in sorted because their path distance is infinite.
        List<Map.Entry<Base, GroundPath>> potential =
                this.availableBases.entrySet()
                        .stream()
                        .filter(p -> !reservedBases.contains(p.getKey()))
                        .sorted(Map.Entry.comparingByValue(new GroundPathComparator()))
                        .collect(Collectors.toList());


        if (potential == null || potential.size() == 0) {
            return null;
        }

        for (int i = 0; i < potential.size(); i++) {
            Base candidate = potential.get(i).getKey();
            if (this.myBases.size() == 1 && candidate.getGeysers().size() == 0) {
                continue;
            }
            return candidate;
        }

        return null;
    }
}
