package info;

import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects base and building state.
 *
 */
public class BaseData {

    private bwem.Base mainBase;

    private HashSet<Unit> macroHatcheries = new HashSet<>();
    private HashSet<Unit> baseHatcheries = new HashSet<>();

    private Set<Base> allBases = new HashSet<>();
    private HashSet<Base> myBases = new HashSet<>();

    private HashMap<Unit, Base> baseLookup = new HashMap<>();
    private HashSet<TilePosition> baseTilePositionSet = new HashSet<>();

    public BaseData(List<Base> allBases) {
        for (Base base: allBases) {
            this.allBases.add(base);
            this.baseTilePositionSet.add(base.getLocation());
        }
    }

    public void initializeMainBase(Base base) {
        this.mainBase = base;
    }

    public void addBase(Unit hatchery, Base base) {
        baseHatcheries.add(hatchery);
        myBases.add(base);
        baseLookup.put(hatchery, base);
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
    }

    private void removeMacroHatchery(Unit hatchery) {
        macroHatcheries.remove(hatchery);
    }

    public HashSet<Unit> baseHatcheries() { return baseHatcheries; }

    public int currentBaseCount() { return baseHatcheries.size(); }

    public int numHatcheries() { return myBases.size() + macroHatcheries.size(); }

    public TilePosition mainBasePosition() { return mainBase.getLocation(); }

    public boolean isBaseTilePosition(TilePosition tilePosition) {
        return baseTilePositionSet.contains(tilePosition);
    }

    // TODO: Consider walking path, prioritize gas bases over mineral only
    public Base findNewBase() {
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
}
