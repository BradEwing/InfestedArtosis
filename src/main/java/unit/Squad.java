package unit;

import bwapi.Position;
import bwapi.TilePosition;
import lombok.Data;

import java.util.HashSet;

@Data
/**
 * Bundles up managed units that should be functioning together to perform a goal.
 * TODO: Squad merging/splitting
 * TODO: Debug squads
 * TODO: Move into new namespace
 */
public class Squad {

    private HashSet<ManagedUnit> members = new HashSet<>();

    private Position center;
    private int radius;

    public void onFrame() {
        calculateCenter();
    }

    public void addUnit(ManagedUnit managedUnit) {
        members.add(managedUnit);
    }

    public void removeUnit(ManagedUnit managedUnit) {
        members.remove(managedUnit);
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

    // Determine center and radius
    private void computeSquadLocation() {

    }
}
