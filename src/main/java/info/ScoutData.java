package info;

import bwapi.Game;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import info.map.GameMap;
import info.map.MapTile;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ScoutData {
    private HashSet<TilePosition> scoutTargets = new HashSet<>();
    @Getter
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();
    @Getter
    private HashSet<TilePosition> enemyBuildingPositions = new HashSet<>();

    private HashMap<Base, Integer> baseScoutAssignments = new HashMap<>(); // TODO: Rename to startingBaseScoutAssignments

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
     */
    public boolean shouldOverlordsContinueScouting(Race enemyRace, Set<Unit> enemies) {
        switch (enemyRace) {
            case Terran:
                return !hasTerranScoutingConditions(enemies);
            case Protoss:
                return !hasProtossScoutingConditions(enemies);
            case Zerg:
                return !hasZergScoutingConditions(enemies);
            case Unknown:
                return true;
            default:
                return false;
        }
    }

    private boolean hasTerranScoutingConditions(Set<Unit> enemies) {
        for (Unit unit : enemies) {
            if (unit.getType() == bwapi.UnitType.Terran_Marine) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Terran_Barracks) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProtossScoutingConditions(Set<Unit> enemies) {
        for (Unit unit : enemies) {
            if (unit.getType() == bwapi.UnitType.Protoss_Dragoon) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Protoss_Corsair) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Protoss_Cybernetics_Core) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Protoss_Stargate) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Protoss_Photon_Cannon) {
                return true;
            }
        }
        return false;
    }

    private boolean hasZergScoutingConditions(Set<Unit> enemies) {
        for (Unit unit : enemies) {
            if (unit.getType() == bwapi.UnitType.Zerg_Spire) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Zerg_Mutalisk) {
                return true;
            }
            if (unit.getType() == bwapi.UnitType.Zerg_Hydralisk) {
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
        return baseScoutAssignments.get(base); 
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

    public TilePosition pollScoutTarget(Game game, GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        if (baseData.getMainEnemyBase() == null && !isEnemyBuildingLocationKnown()) {
            Base baseTarget = fetchBaseRoundRobin(getScoutingBaseSet());
            if (baseTarget != null) {
                int assignments = getScoutsAssignedToBase(baseTarget);
                updateBaseScoutAssignment(baseTarget, assignments);
                return baseTarget.getLocation();
            }
        }

        if (isEnemyBuildingLocationKnown()) {
            for (TilePosition target: enemyBuildingPositions) {
                if (!hasScoutTarget(target) && !game.isVisible(target)) {
                    return target;
                }
            }
        }

        TilePosition scoutTile = getHotScoutTile(gameState);
        if (scoutTile != null) return scoutTile;

        return findNewActiveScoutTarget();
    }

    @Nullable
    private TilePosition getHotScoutTile(GameState gameState) {
        GameMap gameMap = gameState.getGameMap();
        ArrayList<MapTile> heatMap = gameMap.getHeatMap();
        if (!heatMap.isEmpty()) {
            MapTile scoutTile = heatMap.get(0);
            scoutTile.setScoutImportance(0);
            gameMap.ageHeatMap();
            return scoutTile.getTile();
        }
        return null;
    }

    private Base fetchBaseRoundRobin(Set<Base> candidateBases) {
        Base leastScoutedBase = null;
        Integer fewestScouts = Integer.MAX_VALUE;
        for (Base base: candidateBases) {
            Integer assignedScoutsToBase = getScoutsAssignedToBase(base);
            if (assignedScoutsToBase < fewestScouts) {
                leastScoutedBase = base;
                fewestScouts = assignedScoutsToBase;
            }
        }
        return leastScoutedBase;
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
}
