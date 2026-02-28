package info;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.exception.NoWalkablePathException;
import info.map.GameMap;
import info.map.GroundPath;
import info.map.GroundPathComparator;
import lombok.Getter;
import lombok.Setter;
import util.Distance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collects base and building state.
 *
 */
public class BaseData {

    private Base mainBase;
    private Base naturalExpansion;
    @Getter
    private Base inferredNaturalBase;
    private Base mainEnemyBase;
    @Getter
    private boolean enemyMainBaseFound = false;
    private HashSet<Unit> macroHatcheries = new HashSet<>();
    private HashSet<Unit> baseHatcheries = new HashSet<>();
    private HashSet<Base> allBases = new HashSet<>();
    @Getter
    private HashSet<Base> myBases = new HashSet<>();
    @Getter
    private HashSet<Base> reservedBases = new HashSet<>();
    @Getter
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
    private HashMap<Unit, TilePosition> geyserPositionLookup = new HashMap<>();
    private HashMap<Base, Integer> sunkenColonyLookup = new HashMap<>();
    private HashMap<Base, Integer> sunkenColonyReserveLookup = new HashMap<>();
    private HashMap<Base, Integer> sporeColonyLookup = new HashMap<>();
    private HashMap<Base, Integer> sporeColonyReserveLookup = new HashMap<>();
    @Setter
    private boolean allowSunkenAtMain = false;

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

        this.inferredNaturalBase = availableBases.entrySet().stream()
                .filter(e -> !e.getKey().getGeysers().isEmpty())
                .min(Map.Entry.comparingByValue(new GroundPathComparator()))
                .map(Map.Entry::getKey)
                .orElse(null);

        map.calculateMainBaseTiles(this.mainBasePosition());
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

        for (bwem.Geyser g : base.getGeysers()) {
            Unit geyserUnit = g.getUnit();
            geyserPositionLookup.put(geyserUnit, g.getTopLeft());
            if (!geyserUnit.getType().isRefinery()) {
                availableGeysers.add(geyserUnit);
            }
        }
    }

    public Set<Base> availableBases() {
        return availableBases.keySet();
    }

    public boolean canReserveExtractor() { 
        return availableGeysers.size() > 0; 
    }

    public Unit reserveExtractor() {
        Unit candidate = availableGeysers.iterator().next();
        extractors.add(candidate);
        availableGeysers.remove(candidate);
        return candidate;
    }

    public void unreserveExtractor(TilePosition tilePosition) {
        Unit geyser = null;
        for (Unit u : extractors) {
            TilePosition storedPosition = geyserPositionLookup.get(u);
            if (storedPosition != null && storedPosition.equals(tilePosition)) {
                geyser = u;
                break;
            }
        }
        if (geyser != null) {
            extractors.remove(geyser);
            if (!geyser.getType().isRefinery()) {
                availableGeysers.add(geyser);
            }
        }
    }

    public boolean isExtractorAtPosition(TilePosition position) {
        for (Map.Entry<Unit, TilePosition> entry : geyserPositionLookup.entrySet()) {
            if (entry.getValue().equals(position)) {
                return entry.getKey().getType().isRefinery();
            }
        }
        return false;
    }

    public void onGeyserComplete(Unit geyser) {
        TilePosition geyserTp = geyser.getTilePosition();
        for (Unit existing : availableGeysers) {
            if (existing.getTilePosition().equals(geyserTp)) {
                return;
            }
        }
        for (Unit existing : extractors) {
            TilePosition storedPos = geyserPositionLookup.get(existing);
            if (storedPos != null && storedPos.equals(geyserTp)) {
                return;
            }
        }
        for (Base base : myBases) {
            for (bwem.Geyser baseGeyser : base.getGeysers()) {
                if (baseGeyser.getUnit().getTilePosition().equals(geyserTp)) {
                    availableGeysers.add(geyser);
                    geyserPositionLookup.put(geyser, geyserTp);
                    return;
                }
            }
        }
    }

    public void onGeyserShow(Unit geyser) {
        if (geyserPositionLookup.containsKey(geyser)) {
            geyserPositionLookup.put(geyser, geyser.getTilePosition());
        }
    }

    public TilePosition getGeyserPosition(Unit geyser) {
        return geyserPositionLookup.get(geyser);
    }

    public TilePosition findUnassignedExtractorPosition(Set<TilePosition> excludePositions) {
        for (Unit geyser : extractors) {
            TilePosition pos = geyserPositionLookup.get(geyser);
            if (pos != null && !excludePositions.contains(pos)) {
                return pos;
            }
        }
        return null;
    }

    public int numExtractor() {
        return extractors.size();
    }

    public int numMacroHatcheries() {
        return macroHatcheries.size(); 
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

    public HashSet<Unit> baseHatcheries() { 
        return baseHatcheries; 
    }

    public int currentBaseCount() { 
        return baseHatcheries.size(); 
    }

    public int currentAndReservedCount() { 
        return myBases.size() + reservedBases.size(); 
    }

    public int numHatcheries() { 
        return myBases.size() + macroHatcheries.size() + reservedBases.size(); 
    }

    public TilePosition mainBasePosition() { 
        return mainBase.getLocation(); 
    }

    public TilePosition naturalExpansionPosition() {
        return naturalExpansion.getLocation();
    }

    public boolean hasNaturalExpansion() {
        return naturalExpansion != null;
    }

    public boolean isBaseTilePosition(TilePosition tilePosition) {
        return baseTilePositionSet.contains(tilePosition);
    }

    public Base baseAtTilePosition(TilePosition tilePosition) { 
        return baseTilePositionLookup.get(tilePosition); 
    }

    /**
     * Finds a new base.
     *
     * <p>The algorithm selects an unclaimed base that is:
     * <ul>
     *   <li>Closest to our main base (i.e., minimal ground path distance)
     *   <li>If the enemy's main base is known, also as far from the enemy as possible.
     * </ul>
     *
     * <p>If enemy main base is known, for each candidate base a score is computed as:
     * <br>
     *     score = (ground path distance from main base) - (Euclidean distance to enemy main base)
     * <br>
     * A lower score indicates a candidate that is near our main base and far from the enemy.
     *
     * <p>If the enemy main base is not known, the candidate with the smallest ground path distance is chosen.
     *
     * <p>Note: When selecting the natural expansion (i.e., when only one base exists),
     * mineral-only bases (without geysers) are skipped.
     *
     * <p>Islands (bases with no walkable ground path) are excluded since they are not present in availableBases.
     *
     * @return the best candidate base according to the criteria, or null if no valid base is available.
     */
    public Base findNewBase() {
        // Build a list of candidate bases that are not already reserved.
        List<Map.Entry<Base, GroundPath>> potential = this.availableBases.entrySet()
                .stream()
                .filter(p -> !reservedBases.contains(p.getKey()))
                .collect(Collectors.toList());

        // If only one base exists, skip bases with no geysers (mineral-only bases)
        if (this.myBases.size() == 1) {
            potential = potential.stream()
                    .filter(e -> !e.getKey().getGeysers().isEmpty())
                    .collect(Collectors.toList());
        }

        if (potential.isEmpty()) {
            return null;
        }

        if (knowEnemyMainBase()) {
            Base enemyBase = getMainEnemyBase();
            // Compute a score for each candidate: lower is better.
            // score = (ground path distance from main base) - (Euclidean distance to enemy main base)
            // This favors bases that are near our main base and far from the enemy.
            try {
                return potential.stream()
                        .min((e1, e2) -> {
                            double score1 = e1.getValue().getGroundDistance() -
                                    e1.getKey().getLocation().getDistance(enemyBase.getLocation());
                            double score2 = e2.getValue().getGroundDistance() -
                                    e2.getKey().getLocation().getDistance(enemyBase.getLocation());
                            return Double.compare(score1, score2);
                        })
                        .map(Map.Entry::getKey)
                        .orElse(null);
            } catch (Exception e) {
                return null;
            }
        } else {
            // Otherwise, sort solely by the ground path distance from our main base.
            potential.sort(Map.Entry.comparingByValue(new GroundPathComparator()));
            return potential.get(0).getKey();
        }
    }

    // Called on onUnitDestroy
    public void removeSunkenColony(Unit sunken) {
        if (sunken.getType() != UnitType.Zerg_Sunken_Colony) {
            return;
        }
        Base base = myBases.stream()
                .sorted(Distance.closestBaseTo(sunken))
                .collect(Collectors.toList())
                .get(0);
        sunkenColonyLookup.put(base, Math.max(sunkenColonyLookup.getOrDefault(base, 0) - 1, 0));
    }

    // Called for onUnitComplete
    public void addSunkenColony(Unit sunken) {
        Base base = myBases.stream()
                .sorted(Distance.closestBaseTo(sunken))
                .collect(Collectors.toList())
                .get(0);
        sunkenColonyLookup.put(base, sunkenColonyLookup.getOrDefault(base, 0) + 1);
        sunkenColonyReserveLookup.put(base, Math.max(sunkenColonyReserveLookup.getOrDefault(base, 0) - 1, 0));
    }

    public void reserveSunkenColony(Base base) {
        sunkenColonyReserveLookup.put(base, sunkenColonyReserveLookup.getOrDefault(base, 0) + 1);
    }

    public boolean isEligibleForSunkenColony(Base base) {
        if (base == mainBase && !allowSunkenAtMain) {
            return false;
        }
        if (islands.contains(base)) {
            return false;
        }
        return true;
    }

    public int sunkensPerBase(Base base) {
        int reserved = sunkenColonyReserveLookup.getOrDefault(base, 0);
        int sunkens = sunkenColonyLookup.getOrDefault(base, 0);
        return reserved + sunkens;
    }

    public int getTotalSunkenCount() {
        int total = 0;
        for (Integer count : sunkenColonyLookup.values()) {
            total += count;
        }
        for (Integer count : sunkenColonyReserveLookup.values()) {
            total += count;
        }
        return total;
    }

    public void removeSporeColony(Unit spore) {
        if (spore.getType() != UnitType.Zerg_Spore_Colony) {
            return;
        }
        Base base = myBases.stream()
                .sorted(Distance.closestBaseTo(spore))
                .collect(Collectors.toList())
                .get(0);
        sporeColonyLookup.put(base, Math.max(sporeColonyLookup.getOrDefault(base, 0) - 1, 0));
    }

    public void addSporeColony(Unit spore) {
        Base base = myBases.stream()
                .sorted(Distance.closestBaseTo(spore))
                .collect(Collectors.toList())
                .get(0);
        sporeColonyLookup.put(base, sporeColonyLookup.getOrDefault(base, 0) + 1);
        sporeColonyReserveLookup.put(base, Math.max(sporeColonyReserveLookup.getOrDefault(base, 0) - 1, 0));
    }

    public void reserveSporeColony(Base base) {
        sporeColonyReserveLookup.put(base, sporeColonyReserveLookup.getOrDefault(base, 0) + 1);
    }

    public boolean isEligibleForSporeColony(Base base) {
        if (islands.contains(base)) {
            return false;
        }
        return true;
    }

    public int sporesPerBase(Base base) {
        int reserved = sporeColonyReserveLookup.getOrDefault(base, 0);
        int spores = sporeColonyLookup.getOrDefault(base, 0);
        return reserved + spores;
    }

    public int getTotalSporeCount() {
        int total = 0;
        for (Integer count : sporeColonyLookup.values()) {
            total += count;
        }
        for (Integer count : sporeColonyReserveLookup.values()) {
            total += count;
        }
        return total;
    }

    public Base getMainEnemyBase() {
        return mainEnemyBase;
    }

    /**
     * Adds an enemy base to the tracking structures.
     * @param base The enemy base to add.
     */
    public void addEnemyBase(Base base) {
        if (enemyBases.add(base)) {
            availableBases.remove(base);
            reservedBases.remove(base);
            // Automatically set as main enemy base if it's a starting location and not already set
            if (mains.contains(base) && mainEnemyBase == null) {
                mainEnemyBase = base;
                enemyMainBaseFound = true;
            }
        }
    }

    /**
     * Removes an enemy base from tracking and makes it available if accessible.
     * @param base The enemy base to remove.
     */
    public void removeEnemyBase(Base base) {
        if (enemyBases.remove(base)) {
            if (base == mainEnemyBase) {
                mainEnemyBase = null;
            }
            // Re-add to available bases if not an island
            if (!islands.contains(base)) {
                GroundPath path = allBasePaths.get(base);
                if (path != null) {
                    availableBases.put(base, path);
                }
            }
        }
    }

    public boolean knowEnemyMainBase() { 
        return mainEnemyBase != null; 
    }

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

    public Set<Position> getMyBasePositions() {
        Set<Position> positions = myBases.stream()
                .map(Base::getCenter)
                .collect(Collectors.toSet());
        reservedBases.stream()
                .map(Base::getCenter)
                .forEach(positions::add);
        return positions;
    }

    public int numMains() {
        return mains.size();
    }
}
