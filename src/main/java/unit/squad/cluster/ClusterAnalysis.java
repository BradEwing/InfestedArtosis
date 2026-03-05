package unit.squad.cluster;

import bwapi.UnitType;
import info.tracking.ObservedUnit;

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

    public static List<EnemyCluster> cluster(Set<ObservedUnit> enemies, Set<ObservedUnit> buildings) {
        return cluster(enemies, buildings, DEFAULT_CLUSTER_THRESHOLD);
    }

    public static List<EnemyCluster> cluster(Set<ObservedUnit> enemies, Set<ObservedUnit> buildings, double threshold) {
        List<ObservedUnit> candidates = new ArrayList<>();
        for (ObservedUnit ou : enemies) {
            if (isValidTarget(ou)) {
                candidates.add(ou);
            }
        }
        for (ObservedUnit ou : buildings) {
            UnitType type = ou.getUnitType();
            if (isHostileBuilding(type) && !ou.getUnit().isMorphing() && !ou.getUnit().isBeingConstructed()) {
                candidates.add(ou);
            }
        }

        Set<ObservedUnit> assigned = new HashSet<>();
        List<EnemyCluster> clusters = new ArrayList<>();

        for (ObservedUnit seed : candidates) {
            if (assigned.contains(seed)) continue;

            Set<ObservedUnit> clusterMembers = new HashSet<>();
            Queue<ObservedUnit> frontier = new LinkedList<>();
            frontier.add(seed);
            assigned.add(seed);

            while (!frontier.isEmpty()) {
                ObservedUnit current = frontier.poll();
                clusterMembers.add(current);

                for (ObservedUnit other : candidates) {
                    if (assigned.contains(other)) continue;
                    if (current.getEffectivePosition().getDistance(other.getEffectivePosition()) <= threshold) {
                        assigned.add(other);
                        frontier.add(other);
                    }
                }
            }

            clusters.add(new EnemyCluster(clusterMembers));
        }

        return clusters;
    }

    private static boolean isValidTarget(ObservedUnit ou) {
        UnitType type = ou.getUnitType();
        if (type == UnitType.Unknown) return false;
        if (type.isWorker() || type.isBuilding()) return false;
        if (ou.getUnit().isBeingConstructed() || ou.getUnit().isMorphing()) return false;
        return true;
    }
}
