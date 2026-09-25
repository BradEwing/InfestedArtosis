package info;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import lombok.Getter;
import telemetry.PlanEvents;
import util.Filter;
import util.Time;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ScoutData {
    /**
     * Share of an enemy main's buildable tiles our vision must have covered before the main counts as scouted.
     * A majority: the unseen remainder, where a Gateway could still hide, is then smaller than what was seen.
     */
    public static final double ENEMY_MAIN_SCOUTED_COVERAGE = 0.5;

    /**
     * Tile radius around an enemy main's depot inside which its Gateways stand. Home Gateways and Cores stand
     * within about 400 px of the depot.
     */
    public static final int ENEMY_MAIN_GATEWAY_SITE_TILE_RADIUS = 14;

    /**
     * Share of an enemy main's Gateway sites, its buildable tiles within
     * {@link #ENEMY_MAIN_GATEWAY_SITE_TILE_RADIUS} of the depot, our vision must also have covered before the main
     * counts as scouted.
     */
    public static final double ENEMY_MAIN_GATEWAY_SITE_COVERAGE = 0.9;

    /**
     * Vision of an enemy main counts toward its coverage only from this time on. Earlier vision predates the
     * Gateway it is meant to rule out, which can be up by 1:45.
     */
    public static final Time ENEMY_MAIN_VISION_START = new Time(2, 0);

    private HashSet<TilePosition> scoutTargets = new HashSet<>();
    @Getter
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();
    @Getter
    private HashSet<TilePosition> enemyBuildingPositions = new HashSet<>();

    private HashMap<Base, Integer> baseScoutAssignments = new HashMap<>();

    private final HashMap<Base, Set<TilePosition>> enemyMainSeenTiles = new HashMap<>();
    private final HashMap<Base, Time> enemyMainScoutedFrames = new HashMap<>();

    public void addScoutTarget(TilePosition tp) {
        scoutTargets.add(tp);
    }

    public boolean hasScoutTarget(TilePosition tp) {
        return scoutTargets.contains(tp);
    }

    public boolean hasScoutTargets() {
        return scoutTargets.size() > 0;
    }

    public HashSet<TilePosition> getScoutTargets() {
        return scoutTargets;
    }

    public void removeActiveScoutTarget(TilePosition tp) {
        activeScoutTargets.remove(tp);
    }

    public void removeScoutTarget(TilePosition tp) {
        scoutTargets.remove(tp);
    }

    public void setActiveScoutTarget(TilePosition tp) {
        scoutTargets.remove(tp);
        activeScoutTargets.add(tp);
    }

    public boolean isEnemyBuildingLocationKnown() {
        return !enemyBuildingPositions.isEmpty();
    }

    /**
     * Determines if overlords should continue scouting based on if specific enemy units/buildings are detected.
     *
     * Against Terran, Protoss and an unknown race any visible air threat ends scouting. Against Zerg the
     * overlord stays over the enemy base, since zerglings and drones cannot shoot up, until air or
     * hydralisk tech or a unit that depends on it is seen.
     */
    public boolean shouldOverlordsContinueScouting(Race enemyRace, Collection<UnitType> enemyTypes) {
        switch (enemyRace) {
            case Terran:
                return !hasAirThreat(enemyTypes) && !hasTerranScoutingConditions(enemyTypes);
            case Protoss:
                return !hasAirThreat(enemyTypes) && !hasProtossScoutingConditions(enemyTypes);
            case Zerg:
                return !hasZergScoutingConditions(enemyTypes);
            case Unknown:
                return !hasAirThreat(enemyTypes);
            default:
                return false;
        }
    }

    private boolean hasAirThreat(Collection<UnitType> enemyTypes) {
        for (UnitType type : enemyTypes) {
            if (Filter.isAirThreat(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTerranScoutingConditions(Collection<UnitType> enemyTypes) {
        for (UnitType type : enemyTypes) {
            if (type == UnitType.Terran_Marine) {
                return true;
            }
            if (type == UnitType.Terran_Barracks) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProtossScoutingConditions(Collection<UnitType> enemyTypes) {
        for (UnitType type : enemyTypes) {
            if (type == UnitType.Protoss_Dragoon) {
                return true;
            }
            if (type == UnitType.Protoss_Corsair) {
                return true;
            }
            if (type == UnitType.Protoss_Cybernetics_Core) {
                return true;
            }
            if (type == UnitType.Protoss_Stargate) {
                return true;
            }
            if (type == UnitType.Protoss_Photon_Cannon) {
                return true;
            }
        }
        return false;
    }

    private boolean hasZergScoutingConditions(Collection<UnitType> enemyTypes) {
        for (UnitType type : enemyTypes) {
            if (type == UnitType.Zerg_Spire) {
                return true;
            }
            if (type == UnitType.Zerg_Hydralisk_Den) {
                return true;
            }
            if (type == UnitType.Zerg_Spore_Colony) {
                return true;
            }
            if (type == UnitType.Zerg_Mutalisk) {
                return true;
            }
            if (type == UnitType.Zerg_Scourge) {
                return true;
            }
            if (type == UnitType.Zerg_Hydralisk) {
                return true;
            }
            if (type == UnitType.Zerg_Lurker) {
                return true;
            }
        }
        return false;
    }

    public void addEnemyBuildingLocation(TilePosition tp) {
        enemyBuildingPositions.add(tp);
    }

    public void removeEnemyBuildingLocation(TilePosition tp) {
        enemyBuildingPositions.remove(tp);
    }

    public int getScoutsAssignedToBase(Base base) {
        return baseScoutAssignments.getOrDefault(base, 0);
    }

    public void removeBaseScoutAssignment(Base base) {
        baseScoutAssignments.remove(base);
    }

    public void addBaseScoutAssignment(Base base) {
        baseScoutAssignments.put(base, 0);
    }

    public void updateBaseScoutAssignment(Base base, int assignments) {
        baseScoutAssignments.put(base, assignments + 1);
    }

    /**
     *
     * @return Set<Base> containing main bases that have not been scouted
     */
    public Set<Base> getScoutingBaseSet() { 
        return baseScoutAssignments.keySet(); 
    }

    /**
     * Find a new active scout target from unsearched scout target candidates
     * @return TilePosition
     */
    public TilePosition findNewActiveScoutTarget() {
        for (TilePosition target: scoutTargets) {
            if (!activeScoutTargets.contains(target)) {
                return target;
            }
        }

        return null;
    }

    /**
     * Clear all scouting data associated with a base that has been scouted/seen.
     */
    public void clearScoutedBase(Base base) {
        if (base == null) {
            return;
        }
        TilePosition tp = base.getLocation();
        activeScoutTargets.remove(tp);
        scoutTargets.remove(tp);
        enemyBuildingPositions.remove(tp);
        removeBaseScoutAssignment(base);
    }

    /**
     * Adds the tiles of this enemy main now in our vision to the tiles seen so far, and records the frame on
     * which the tiles seen first cover {@link #ENEMY_MAIN_SCOUTED_COVERAGE} of the main's buildable tiles and
     * {@link #ENEMY_MAIN_GATEWAY_SITE_COVERAGE} of its Gateway sites. Only the first such frame is kept, and
     * reported as an ENEMY_MAIN_SCOUTED plan event; vision before {@link #ENEMY_MAIN_VISION_START} is ignored.
     *
     * @param visibleTiles buildable tiles of the enemy main's area in our vision this frame
     * @param mainTileCount buildable tiles in the enemy main's area
     * @param gatewaySites buildable tiles of the enemy main's area that are Gateway sites
     */
    public void recordEnemyMainVision(Base enemyMain, Collection<TilePosition> visibleTiles, int mainTileCount,
                                      Collection<TilePosition> gatewaySites, Time frame) {
        if (!ENEMY_MAIN_VISION_START.lessThanOrEqual(frame)) {
            return;
        }
        Set<TilePosition> seen = enemyMainSeenTiles.computeIfAbsent(enemyMain, base -> new HashSet<>());
        seen.addAll(visibleTiles);
        int seenGatewaySites = (int) gatewaySites.stream().filter(seen::contains).count();
        if (isScouted(seen.size(), mainTileCount, seenGatewaySites, gatewaySites.size())
                && enemyMainScoutedFrames.putIfAbsent(enemyMain, frame) == null && enemyMain != null) {
            PlanEvents.enemyMainScouted(enemyMain.getLocation());
        }
    }

    /**
     * Whether a tile is a Gateway site of the main whose depot stands at depotTile: within
     * {@link #ENEMY_MAIN_GATEWAY_SITE_TILE_RADIUS} tiles of it.
     */
    public static boolean isGatewaySite(TilePosition tile, TilePosition depotTile) {
        int dx = tile.getX() - depotTile.getX();
        int dy = tile.getY() - depotTile.getY();
        return dx * dx + dy * dy <= ENEMY_MAIN_GATEWAY_SITE_TILE_RADIUS * ENEMY_MAIN_GATEWAY_SITE_TILE_RADIUS;
    }

    public boolean hasSeenEnemyMainTile(Base enemyMain, TilePosition tile) {
        Set<TilePosition> seen = enemyMainSeenTiles.get(enemyMain);
        return seen != null && seen.contains(tile);
    }

    /**
     * @return the first frame our vision had covered {@link #ENEMY_MAIN_SCOUTED_COVERAGE} of this enemy main's
     *     buildable tiles and {@link #ENEMY_MAIN_GATEWAY_SITE_COVERAGE} of its Gateway sites, or null if it never
     *     has
     */
    public Time getEnemyMainScoutedFrame(Base enemyMain) {
        return enemyMainScoutedFrames.get(enemyMain);
    }

    /**
     * Whether the tiles seen cover enough of the main and of its Gateway sites. A main with no Gateway sites
     * gives no ground where a Gateway would be seen, so it never counts as scouted.
     */
    static boolean isScouted(int seenTiles, int mainTileCount, int seenGatewaySites, int gatewaySiteCount) {
        return mainTileCount > 0 && seenTiles >= ENEMY_MAIN_SCOUTED_COVERAGE * mainTileCount
                && gatewaySiteCount > 0 && seenGatewaySites >= ENEMY_MAIN_GATEWAY_SITE_COVERAGE * gatewaySiteCount;
    }
}
