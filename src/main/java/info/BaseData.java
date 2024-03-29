package info;

import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import info.exception.NoWalkablePathException;
import info.map.GameMap;
import info.map.GroundPath;
import info.map.GroundPathComparator;
import util.BaseUnitDistanceComparator;

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

    private Base mainBase;
    private Base naturalExpansion;

    private Base mainEnemyBase;

    private HashSet<Unit> macroHatcheries = new HashSet<>();
    private HashSet<Unit> baseHatcheries = new HashSet<>();

    private HashSet<Base> allBases = new HashSet<>();
    private HashSet<Base> myBases = new HashSet<>();
    private HashSet<Base> reservedBases = new HashSet<>();
    private HashSet<Base> enemyBases = new HashSet<>();
    private HashSet<Base> islands = new HashSet<>();
    private HashSet<Base> mineralOnlyBase = new HashSet<>();
    private HashSet<Base> mains = new HashSet<>();

    private HashMap<Unit, Base> baseLookup = new HashMap<>();
    private HashSet<TilePosition> baseTilePositionSet = new HashSet<>();
    private HashMap<TilePosition, Base> baseTilePositionLookup = new HashMap<>();

    private HashMap<Base, GroundPath> allBasePaths = new HashMap<>();
    private HashMap<Base, GroundPath> availableBases = new HashMap<>();

    private HashSet<Unit> extractors = new HashSet<>();
    private HashSet<Unit> availableGeysers = new HashSet<>();

    // Sunken Colony Tracking
    // Only take 1 per base for now
    private HashMap<Base, Unit> sunkenColonyLookup = new HashMap<>();
    private int reservedSunkenColonies = 0;

    public BaseData(List<Base> allBases) {
        for (Base base: allBases) {
            this.allBases.add(base);
            this.baseTilePositionSet.add(base.getLocation());
            this.baseTilePositionLookup.put(base.getLocation(), base);
            if (base.getGeysers().size() == 0) {
                mineralOnlyBase.add(base);
            }
            if (base.isStartingLocation()) {
                mains.add(base);
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

    public Base getMainBase() {
        return this.mainBase;
    }

    public void addBase(Unit hatchery, Base base) {
        baseHatcheries.add(hatchery);
        myBases.add(base);
        baseLookup.put(hatchery, base);
        availableBases.remove(base);
        reservedBases.remove(base);

        if (naturalExpansion == null && myBases.size() > 1) {
            naturalExpansion = base;
        }

        base.getGeysers().stream().forEach(g -> availableGeysers.add(g.getUnit()));
    }

    public boolean canReserveExtractor() { return availableGeysers.size() > 0; }

    public Unit reserveExtractor() {
        Unit candidate = availableGeysers.iterator().next();
        extractors.add(candidate);
        availableGeysers.remove(candidate);
        return candidate;
    }

    // TODO: Call when drone scheduled to build extractor dies
    public void addExtractorCandidate(Unit geyser) {

    }

    public int numExtractor() {
        return extractors.size();
    }

    public Base reserveBase() {
        final Base base = this.findNewBase();
        if (base == null) {
            return null;
        }
        reservedBases.add(base);
        return base;
    }

    public void cancelReserveBase(Base base) {
        GroundPath oldPath = allBasePaths.get(base);
        availableBases.put(base, oldPath);
        reservedBases.remove(base);
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
        if (baseHatcheries.contains(hatchery)) {
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

    public TilePosition naturalExpansionPosition() {
        return naturalExpansion.getLocation();
    }

    public boolean hasNaturalExpansion() {
        return naturalExpansion != null;
    }

    public boolean isBaseTilePosition(TilePosition tilePosition) {
        return baseTilePositionSet.contains(tilePosition);
    }

    public Base baseAtTilePosition(TilePosition tilePosition) { return baseTilePositionLookup.get(tilePosition); }

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

    // TODO: Track by creep colony
    // Called for onUnitComplete
    public void addSunkenColony(Unit sunken) {
        Base base = myBases.stream()
                .sorted(new BaseUnitDistanceComparator(sunken))
                .collect(Collectors.toList())
                .get(0);
        sunkenColonyLookup.put(base, sunken);
        if (reservedSunkenColonies > 0) {
            reservedSunkenColonies -= 1;
        }
    }

    public TilePosition reserveSunkenColony() {
        Base base = myBases.stream()
                .filter(b -> !sunkenColonyLookup.containsKey(b))
                .collect(Collectors.toList())
                .iterator()
                .next();
        reservedSunkenColonies += 1;
        return base.getLocation();
    }

    public boolean canPlanSunkenColony() {
        return myBases.size() - reservedSunkenColonies - sunkenColonyLookup.size() > 0;
    }

    // TODO: Remove main enemy base when area is clear
    public Base getMainEnemyBase() {
        return mainEnemyBase;
    }

    public void setMainEnemyBase(Base base) {
        mainEnemyBase = base;
        enemyBases.add(base);
        availableBases.remove(base);
    }

    public boolean knowEnemyMainBase() { return mainEnemyBase != null; }

    /**
     * Find the farthest starting base
     * @return
     */
    public Base findFarthestStartingBaseByGround() {
        Base best = null;
        for (Base b: mains) {
            if (b == mainBase) {
                continue;
            }

            if (best == null) {
                best = b;
                continue;
            }
            GroundPath bestPath = allBasePaths.get(best);
            GroundPath candidatePath = allBasePaths.get(b);
            if (candidatePath.getGroundDistance() > bestPath.getGroundDistance()) {
                best = b;
            }
        }

        return best;
    }

    public int numMains() { return mains.size(); }
}
