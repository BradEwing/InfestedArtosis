package telemetry;

import bwapi.Position;
import lombok.Getter;
import unit.managed.ManagedUnit;
import unit.squad.Squad;

/**
 * One of our fighters found within contact radius of an enemy on a sample frame.
 */
@Getter
final class Contact {

    private final ManagedUnit managedUnit;
    private final Squad squad;
    private final Position position;

    Contact(ManagedUnit managedUnit, Squad squad, Position position) {
        this.managedUnit = managedUnit;
        this.squad = squad;
        this.position = position;
    }
}
