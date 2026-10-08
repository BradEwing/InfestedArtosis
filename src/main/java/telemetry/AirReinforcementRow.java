package telemetry;

import bwapi.Position;
import lombok.Builder;
import lombok.Getter;
import unit.squad.SquadStatus;

/**
 * One row of telemetry_air_reinforcement.csv: a Mutalisk hatching, attacking for the first time or dying, a change in
 * the number of active air squads, or a rallying air squad routed to, refused, or joining an active air squad.
 *
 * <p>Counts and measures left at -1 were not evaluated for the row's event.
 */
@Getter
@Builder
public final class AirReinforcementRow {

    /**
     * What the row describes.
     */
    public enum Event {
        HATCH,
        FIRST_ATTACK,
        DEATH,
        ACTIVE_COUNT,
        ROUTE,
        REFUSED,
        JOIN,
        HOLD,
        DROP
    }

    private final int frame;
    private final Event event;
    @Builder.Default
    private final int unitId = -1;
    private final String squadId;
    private final SquadStatus squadStatus;
    private final String targetSquadId;
    private final SquadStatus targetStatus;
    private final Position center;
    private final Position targetCenter;
    @Builder.Default
    private final int mutas = -1;
    @Builder.Default
    private final int waypoints = -1;
    @Builder.Default
    private final double pathLength = -1;
    @Builder.Default
    private final double nearestMateDistance = -1;
    @Builder.Default
    private final int activeAirSquads = -1;
    @Builder.Default
    private final int hatchFrame = -1;
    @Builder.Default
    private final double nearestSquadMateDistance = -1;
    @Builder.Default
    private final int zoneThreats = -1;
    @Builder.Default
    private final double detour = -1;
    @Builder.Default
    private final int inFlight = -1;
    @Builder.Default
    private final int linkFrames = -1;
}
