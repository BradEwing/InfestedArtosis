package telemetry;

import bwapi.Position;
import lombok.Builder;
import lombok.Getter;
import unit.squad.AirFlock;
import unit.squad.SquadStatus;

import java.util.Collection;

/**
 * One row of telemetry_flock.csv: how spread an air squad's Mutalisks are on a sampled frame, or how far a lost
 * Mutalisk was from its nearest Mutalisk squad-mate, or which Mutalisk started regrouping on its flock.
 *
 * <p>Counts and measures left at -1 were not evaluated for the row's event, and so are regrouping ids and the
 * retreat branch left null. The retreat counts of a SAMPLE row, and the retreat branch of a MUTA_LOST row, are
 * evaluated only for a squad in RETREAT.
 */
@Getter
@Builder
public final class FlockRow {

    /**
     * What the row describes.
     */
    public enum Event {
        SAMPLE,
        MUTA_LOST,
        REGROUP
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
    private final Collection<Integer> regroupingIds;
    @Builder.Default
    private final int regroupingArmed = -1;
    @Builder.Default
    private final int lastMuta = -1;
    @Builder.Default
    private final int retreatShared = -1;
    @Builder.Default
    private final int retreatAnchor = -1;
    @Builder.Default
    private final int retreatFlee = -1;
    private final AirFlock.RetreatBranch retreatBranch;
    @Builder.Default
    private final int edgeMembers = -1;
    @Builder.Default
    private final int edgeReleases = -1;
}
