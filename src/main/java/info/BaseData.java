package info;

import bwapi.Unit;
import bwem.Base;

import java.util.HashSet;

/**
 * Collects base and building state.
 *
 */
public class BaseData {

    private bwem.Base mainBase;

    private HashSet<Unit> bases = new HashSet<>();
    private HashSet<Base> allBases = new HashSet<>();
    private HashSet<Base> myBases = new HashSet<>(); // Formerly baseLocations
    private HashSet<Unit> macroHatcheries = new HashSet<>();

    public BaseData(HashSet<Base> allBases) {
        this.allBases = allBases;
    }

    public void addBase(Base base) {
        myBases.add(base);
    }

    public void initializeMainBase(Base base) {
        this.mainBase = base;
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
