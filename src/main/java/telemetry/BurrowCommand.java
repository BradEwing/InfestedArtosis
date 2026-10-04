package telemetry;

import bwapi.Position;
import lombok.Builder;
import lombok.Value;

/**
 * One burrow or unburrow command a Lurker issued, as {@link BurrowSink} receives it.
 */
@Value
@Builder
public class BurrowCommand {
    int frame;
    int unitId;
    /**
     * True for a burrow command, false for an unburrow command.
     */
    boolean burrow;
    BurrowReason reason;
    /**
     * Name of the Lurker's role.
     */
    String role;
    /**
     * Where the Lurker stood.
     */
    Position position;
    int hitPoints;
    /**
     * The Lurker's contain point, or null when it has none.
     */
    Position containPoint;
    /**
     * Where an under-fire withdrawal sends the Lurker, or null for any other command.
     */
    Position withdrawPoint;
    /**
     * How many zones of shooters that outrange the Lurker cover it, -1 for any other command.
     */
    @Builder.Default
    int withdrawZones = -1;
}
