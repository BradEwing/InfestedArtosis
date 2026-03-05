package unit.squad.cluster;

import bwapi.Unit;
import bwapi.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static util.Filter.isHostileBuilding;

public final class ClusterAnalysis {

    private static final double DEFAULT_CLUSTER_THRESHOLD = 512.0;

    private ClusterAnalysis() {}

    public static List<EnemyCluster> cluster(Set<Unit> enemies, Set<Unit> buildings) {
        return cluster(enemies, buildings, DEFAULT_CLUSTER_THRESHOLD);
    }

    public static List<EnemyCluster> cluster(Set<Unit> enemies, Set<Unit> buildings, double threshold) {
        List<Unit> candidates = new ArrayList<>();
        for (Unit u : enemies) {
            if (isValidTarget(u)) {
                candidates.add(u);
            }
        }
        for (Unit b : buildings) {
            if (isHostileBuilding(b.getType()) && !b.isMorphing() && !b.isBeingConstructed()) {
                candidates.add(b);
            }
        }

        Set<Unit> assigned = new HashSet<>();
        List<EnemyCluster> clusters = new ArrayList<>();

        for (Unit seed : candidates) {
            if (assigned.contains(seed)) continue;

            Set<Unit> clusterMembers = new HashSet<>();
            Queue<Unit> frontier = new LinkedList<>();
            frontier.add(seed);
            assigned.add(seed);

            while (!frontier.isEmpty()) {
                Unit current = frontier.poll();
                clusterMembers.add(current);

                for (Unit other : candidates) {
                    if (assigned.contains(other)) continue;
                    if (current.getPosition().getDistance(other.getPosition()) <= threshold) {
                        assigned.add(other);
                        frontier.add(other);
                    }
                }
            }

            clusters.add(new EnemyCluster(clusterMembers));
        }

        return clusters;
    }

    private static boolean isValidTarget(Unit unit) {
        UnitType type = unit.getType();
        if (type == UnitType.Unknown) return false;
        if (type.isWorker()) return false;
        if (unit.isBeingConstructed() || unit.isMorphing()) return false;
        if (type.isBuilding()) return false;
        return true;
    }
}
