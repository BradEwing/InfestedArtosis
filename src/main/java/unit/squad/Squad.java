package unit.squad;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
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
public class Squad {

    private final String id = UUID.randomUUID().toString();

    private HashSet<ManagedUnit> members = new HashSet<>();

    // TODO: maybe this is consolidated with retreat target
    private TilePosition rallyPoint;

    @Getter(AccessLevel.NONE)
    private Position center;
    private int radius;
    private SquadStatus status;
    private UnitType squadType = null;

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
        List<Double> distances = new ArrayList<>();
        double tot = 0;

        for (ManagedUnit u: members) {
            double d = center.getDistance(u.getUnit().getPosition());
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

        return outliers / this.size() < 0.25;
    }

    private void checkRegroup() {
        boolean grouped = this.grouped();
        if (status == SquadStatus.FIGHT && !grouped) {
            status = SquadStatus.REGROUP;
            for (ManagedUnit u: members) {
                u.setRole(UnitRole.RALLY);
                u.setRallyPoint(center.toTilePosition());
            }
        }
        if (status == SquadStatus.REGROUP && grouped) {
            for (ManagedUnit u: members) {
                u.setRole(UnitRole.FIGHT);
                u.setRallyPoint(rallyPoint);
            }
        }
    }
}
