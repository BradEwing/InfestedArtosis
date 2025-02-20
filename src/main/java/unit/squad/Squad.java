package unit.squad;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Data
/**
 * Bundles up managed units that should be functioning together to perform a goal.
 */
public class Squad implements Comparable<Squad> {

    private final String id = UUID.randomUUID().toString();

    private HashSet<ManagedUnit> members = new HashSet<>();

    // TODO: maybe this is consolidated with retreat target
    private TilePosition rallyPoint;

    @Getter(AccessLevel.NONE)
    private Position center;
    private SquadStatus status;
    private UnitType type = null;
    private Unit target = null;

    private double max_dx = 0;
    private double max_dy = 0;

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof Squad)) {
            return false;
        }

        Squad s = (Squad) other;

        return this.id.equals(s.getId());
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    public int radius() {
        final double d = Math.sqrt((max_dx*max_dx)+(max_dy*max_dy));
        return (int) d;
    }

    public int distance (Squad other) {
        return (int) center.getDistance(other.getCenter());
    }

    public int distance (ManagedUnit managedUnit) {
        return (int) center.getDistance(managedUnit.getUnit().getPosition());
    }

    public void onFrame() {
        calculateCenter();
        checkRegroup();
    }

    public void addUnit(ManagedUnit managedUnit) {
        members.add(managedUnit);
    }

    public void removeUnit(ManagedUnit managedUnit) {
        members.remove(managedUnit);
    }

    public int size() { return members.size(); }

    public boolean containsManagedUnit(ManagedUnit managedUnit) {
        return members.contains(managedUnit);
    }

    public void merge(Squad other) {
        for (ManagedUnit managedUnit: other.getMembers()) {
            members.add(managedUnit);
        }
    }

    public Position getCenter() {
        if (center == null) {
            calculateCenter();
        }

        return center;
    }

    private void calculateCenter() {
        int x, y;
        x = y = 0;

        for (ManagedUnit managedUnit: members) {
            Position position = managedUnit.getUnit().getPosition();
            x += position.getX();
            y += position.getY();
        }

        this.center = new Position(x / members.size(), y / members.size());
    }

    /*
     * If 25% of the squad is more than 1.5x the average distance from the center, regroup toward the center.
     */
    private boolean grouped() {
        if (this.size() < 2) {
            return true;
        }
        List<Double> distances = new ArrayList<>();
        double tot = 0;

        max_dx = 0;
        max_dy = 0;
        for (ManagedUnit u: members) {
            Position p = u.getUnit().getPosition();
            double d = center.getDistance(p);
            max_dx = Math.max(max_dx, Math.abs(p.getX() - center.getX()));
            max_dy = Math.max(max_dy, Math.abs(p.getY() - center.getY()));
            distances.add(d);
            tot += d;
        }

        double avg = tot / this.size();
        int outliers = 0;
        for (double d: distances) {
            if (d >= avg * 1.5) {
                outliers += 1;
            }
        }

        int x_threshold = 100 + (size() * this.getType().width());
        int y_threshold = 100 + (size() * this.getType().height());
        final boolean lowOutliers = outliers / this.size() < 0.25;
        final boolean lowEccentricity = max_dx < x_threshold && max_dy < y_threshold;

        return lowOutliers && lowEccentricity;
    }

    private void checkRegroup() {
        boolean grouped = this.grouped();
        if (status == SquadStatus.FIGHT && !grouped) {
            status = SquadStatus.REGROUP;
            for (ManagedUnit u: members) {
                u.setRole(UnitRole.REGROUP);
                u.setRallyPoint(center.toTilePosition());
                u.setMovementTargetPosition(center.toTilePosition());
            }
            return;
        }
        if (status == SquadStatus.REGROUP) {
            if (grouped) {
                status = SquadStatus.FIGHT;
                for (ManagedUnit u: members) {
                    u.setRole(UnitRole.FIGHT);
                }
                return;
            }
            for (ManagedUnit u: members) {
                u.setRallyPoint(center.toTilePosition());
            }
        }
    }

    @Override
    public int compareTo(@NotNull Squad o) {
        return Integer.compare(this.size(), o.size());
    }
}
