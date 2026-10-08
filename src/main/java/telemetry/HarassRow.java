package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Builder;
import lombok.Getter;
import unit.squad.AirApproachPricing;
import unit.squad.AirHarassDefenseZones;
import unit.squad.AirHarassEvaluator;
import unit.squad.AirHarassState;
import unit.squad.AirHarassTargeting;

/**
 * One row of telemetry_harass.csv: an air squad's harass entry check, the start, a decision tick, a retarget, a
 * credited kill, a Mutalisk lost, the flock reacting to anti-air, the approach priced against mobile anti-air, a
 * volley committed to a target, or the end of a harass.
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
        EXIT,
        AA_REACTION,
        UNIT_RETARGET,
        EDGE_TURRET,
        ZONE_RECORD,
        ZONE_CLEAR,
        APPROACH_PRICED,
        SNIPE
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
    private final TargetKind targetKind;
    @Builder.Default
    private final double flockDefense = -1;
    @Builder.Default
    private final int aaSightingAge = -1;
    @Builder.Default
    private final int aaKnownCover = -1;
    @Builder.Default
    private final int stalled = -1;
    @Builder.Default
    private final double exposedScore = -1;
    @Builder.Default
    private final double baseScore = -1;
    @Builder.Default
    private final int aaSeenFrame = -1;
    @Builder.Default
    private final int aaTurnFrame = -1;
    @Builder.Default
    private final int aaHitPointsLost = -1;
    private final UnitType aaTriggerType;
    @Builder.Default
    private final int aaTriggerId = -1;
    @Builder.Default
    private final int aaAtTarget = -1;
    @Builder.Default
    private final int edgeTurrets = -1;
    @Builder.Default
    private final int edgeTurretId = -1;
    @Builder.Default
    private final int retargetOldId = -1;
    private final UnitType retargetOldType;
    @Builder.Default
    private final int retargetNewId = -1;
    private final UnitType retargetNewType;
    @Builder.Default
    private final double retargetOldDistance = -1;
    @Builder.Default
    private final double retargetNewDistance = -1;
    private final AirHarassTargeting.Tier retargetOldTier;
    private final AirHarassTargeting.Tier retargetNewTier;
    @Builder.Default
    private final int defenseZones = -1;
    @Builder.Default
    private final int zoneUnits = -1;
    @Builder.Default
    private final int zoneAge = -1;
    private final AirHarassDefenseZones.Cause zoneCause;
    private final AirApproachPricing.Decision approachDecision;
    private final AirApproachPricing.Reason approachReason;
    @Builder.Default
    private final double approachMobileAa = -1;
    @Builder.Default
    private final double approachFlockStrength = -1;
    @Builder.Default
    private final int approachUnits = -1;
    private final UnitType snipeType;
    @Builder.Default
    private final int snipeHitPoints = -1;
    @Builder.Default
    private final int snipeAlpha = -1;
    @Builder.Default
    private final int snipeKilled = -1;

    /**
     * What a harass targets: a known enemy base, or an exposed group of enemies away from a base's heat.
     */
    public enum TargetKind {
        BASE,
        EXPOSED
    }
}
