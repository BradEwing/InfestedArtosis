package unit.squad.cluster;

import bwapi.Position;
import bwapi.Unit;
import info.tracking.ObservedUnit;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public class EnemyCluster {

    private final Set<ObservedUnit> members;
    private final Set<ObservedUnit> visibleMembers;
    private final Position centroid;
    private final SupplyBreakdown supply;
    private final int radius;

    public EnemyCluster(Set<ObservedUnit> members) {
        this.members = members;
        this.visibleMembers = new HashSet<>();
        for (ObservedUnit ou : members) {
            if (ou.getUnit().isVisible()) {
                visibleMembers.add(ou);
            }
        }
        Set<ObservedUnit> geometrySet = visibleMembers.isEmpty() ? members : visibleMembers;
        this.centroid = computeCentroid(geometrySet);
        this.supply = SupplyCalculator.calculateEnemy(members);
        this.radius = computeRadius(geometrySet, centroid);
    }

    public int size() {
        return members.size();
    }

    public Set<Unit> getUnits() {
        Set<Unit> result = new HashSet<>();
        for (ObservedUnit ou : members) {
            result.add(ou.getUnit());
        }
        return result;
    }

    private static Position computeCentroid(Set<ObservedUnit> units) {
        if (units.isEmpty()) return new Position(0, 0);
        int x = 0;
        int y = 0;
        for (ObservedUnit ou : units) {
            Position p = ou.getEffectivePosition();
            x += p.getX();
            y += p.getY();
        }
        return new Position(x / units.size(), y / units.size());
    }

    private static int computeRadius(Set<ObservedUnit> units, Position centroid) {
        int maxDist = 0;
        for (ObservedUnit ou : units) {
            int dist = (int) centroid.getDistance(ou.getEffectivePosition());
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        return maxDist;
    }
}
