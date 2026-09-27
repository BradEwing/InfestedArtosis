package telemetry;

import bwapi.Position;
import lombok.Builder;
import lombok.Getter;
import unit.squad.SquadStatus;

/**
 * One row of telemetry_flock.csv: how spread an air squad's Mutalisks are on a sampled frame, or how far a lost
 * Mutalisk was from its nearest squad-mate.
 *
 * <p>Counts and measures left at -1 were not evaluated for the row's event.
 */
@Getter
@Builder
public final class FlockRow {

    /**
     * What the row describes.
     */
    public enum Event {
        SAMPLE,
        MUTA_LOST
    }

    private final int frame;
    private final String squadId;
    private final Event event;
    private final SquadStatus status;
    @Builder.Default
    private final int mutas = -1;
    private final Position centroid;
    @Builder.Default
    private final double medianDistance = -1;
    @Builder.Default
    private final double maxDistance = -1;
    @Builder.Default
    private final int regrouping = -1;
    @Builder.Default
    private final int unitId = -1;
    @Builder.Default
    private final double nearestMateDistance = -1;
}
