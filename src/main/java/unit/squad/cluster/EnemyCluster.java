package unit.squad.cluster;

import bwapi.Position;
import bwapi.Unit;
import lombok.Getter;

import java.util.Set;

@Getter
public class EnemyCluster {

    private final Set<Unit> members;
    private final Position centroid;
    private final SupplyBreakdown supply;
    private final int radius;

    public EnemyCluster(Set<Unit> members) {
        this.members = members;
        this.centroid = computeCentroid(members);
        this.supply = SupplyCalculator.calculateEnemy(members);
        this.radius = computeRadius(members, centroid);
    }

    public int size() {
        return members.size();
    }

    private static Position computeCentroid(Set<Unit> units) {
        if (units.isEmpty()) return new Position(0, 0);
        int x = 0;
        int y = 0;
        for (Unit u : units) {
            Position p = u.getPosition();
            x += p.getX();
            y += p.getY();
        }
        return new Position(x / units.size(), y / units.size());
    }

    private static int computeRadius(Set<Unit> units, Position centroid) {
        int maxDist = 0;
        for (Unit u : units) {
            int dist = (int) centroid.getDistance(u.getPosition());
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        return maxDist;
    }
}
