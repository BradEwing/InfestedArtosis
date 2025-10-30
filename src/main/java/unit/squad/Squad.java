package unit.squad;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.HashSet;
import java.util.UUID;

@Data
/**
 * Bundles up managed units that should be functioning together to perform a goal.
 */
public class Squad implements Comparable<Squad> {

    private final String id = UUID.randomUUID().toString();

    private HashSet<ManagedUnit> members = new HashSet<>();

    // TODO: maybe this is consolidated with retreat target
    private Position rallyPoint;

    @Getter(AccessLevel.NONE)
    private Position center;
    private SquadStatus status;
    private UnitType type = null;
    private Unit target = null;

    private boolean shouldDisband = false;

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
        if (center == null || other == null) {
            return 0;
        }
        return (int) center.getDistance(other.getCenter());
    }

    public int distance (ManagedUnit managedUnit) {
        try {
            return (int) center.getDistance(managedUnit.getUnit().getPosition());
        } catch (Exception e) {
            return 0;
        }
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

        if (members.size() == 0) {
            this.center = new Position(0, 0);
            this.shouldDisband = true;
            return;
        }

        this.center = new Position(x / members.size(), y / members.size());
    }


    private void checkRegroup() {
        boolean grouped = true;
        if (status == SquadStatus.REGROUP) {
            if (grouped) {
                status = SquadStatus.FIGHT;
                for (ManagedUnit u: members) {
                    u.setRole(UnitRole.FIGHT);
                }
                return;
            }
            for (ManagedUnit u: members) {
                u.setRallyPoint(center);
            }
        }
    }

    @Override
    public int compareTo(@NotNull Squad o) {
        return Integer.compare(this.size(), o.size());
    }

    /**
     * Returns true if this squad should be disbanded due to lack of targets.
     */
    public boolean shouldDisband() {
        return shouldDisband;
    }
}
