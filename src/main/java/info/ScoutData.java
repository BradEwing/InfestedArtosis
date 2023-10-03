package info;

import bwapi.TilePosition;
import bwem.Base;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ScoutData {
    private HashSet<TilePosition> scoutTargets = new HashSet<>();
    private HashSet<TilePosition> activeScoutTargets = new HashSet<>();
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

    public void removeActiveScoutTarget(TilePosition tp) {
        activeScoutTargets.remove(tp);
    }

    public void removeScoutTarget(TilePosition tp) {
        scoutTargets.remove(tp);
    }

    public HashSet<TilePosition> getActiveScoutTargets() { return activeScoutTargets; }

    public void setActiveScoutTarget(TilePosition tp) {
        scoutTargets.remove(tp);
        activeScoutTargets.add(tp);
    }

    public boolean isEnemyBuildingLocationKnown() {
        return enemyBuildingPositions.size() > 0;
    }

    public void addEnemyBuildingLocation(TilePosition tp) {
        enemyBuildingPositions.add(tp);
    }

    public void removeEnemyBuildingLocation(TilePosition tp) {
        enemyBuildingPositions.remove(tp);
    }

    public HashSet<TilePosition> getEnemyBuildingPositions() { return enemyBuildingPositions; }

    public int getScoutsAssignedToBase(Base base) { return baseScoutAssignments.get(base); }

    public void removeBaseScoutAssignment(Base base) {
        baseScoutAssignments.remove(base);
    }

    public void addBaseScoutAssignment(Base base) {
        baseScoutAssignments.put(base, 0);
    }

    public void updateBaseScoutAssignment(Base base, int assignments) {
        baseScoutAssignments.put(base, assignments+1);
    }

    public Set<Base> getScoutingBaseSet() { return baseScoutAssignments.keySet(); }

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
}
