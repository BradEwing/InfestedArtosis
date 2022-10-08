package unit;

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

    private int centerX;
    private int centerY;
    private int radius;

    public void onFrame() {

    }

    public void addUnit(ManagedUnit managedUnit) {
        members.add(managedUnit);
    }

    public void removeUnit(ManagedUnit managedUnit) {
        members.remove(managedUnit);
    }

    // Determine center and radius
    private void computeSquadLocation() {

    }
}
