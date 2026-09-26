package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Builder;
import lombok.Getter;
import unit.squad.AirHarassEvaluator;
import unit.squad.AirHarassState;

/**
 * One row of telemetry_harass.csv: an air squad's harass entry check, the start, a decision tick, a retarget, a
 * credited kill, a Mutalisk lost, or the end of a harass.
 *
 * <p>Counts and measures left at -1 were not evaluated for the row's event.
 */
@Getter
@Builder
public final class HarassRow {

    /**
     * What the row describes.
     */
    public enum Event {
        ENTRY_CHECK,
        ENTER,
        TICK,
        RETARGET,
        KILL,
        MUTA_LOST,
        EXIT
    }

    private final int frame;
    private final String squadId;
    private final Event event;
    private final AirHarassEvaluator.EntryVerdict verdict;
    private final AirHarassEvaluator.ExitReason exitReason;
    private final AirHarassState.Phase phase;
    private final Position base;
    private final Position strikePoint;
    private final Position center;
    @Builder.Default
    private final int mutas = -1;
    @Builder.Default
    private final int healthyMutas = -1;
    @Builder.Default
    private final int flockHitPoints = -1;
    @Builder.Default
    private final double hpLossFraction = -1;
    @Builder.Default
    private final double tolerance = -1;
    @Builder.Default
    private final double airDefense = -1;
    @Builder.Default
    private final int avoidedZones = -1;
    @Builder.Default
    private final int workersKilled = -1;
    @Builder.Default
    private final int buildingsKilled = -1;
    @Builder.Default
    private final int otherKilled = -1;
    @Builder.Default
    private final int mutasLost = -1;
    private final UnitType killedType;
    @Builder.Default
    private final double containDistance = -1;
    @Builder.Default
    private final int basesUnderAttack = -1;
}
