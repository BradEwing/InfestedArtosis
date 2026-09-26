package macro.plan;

import bwapi.TilePosition;
import bwapi.Unit;
import lombok.Getter;
import unit.managed.BuilderStall;
import unit.managed.UnitRole;

/**
 * What a plan's executor was doing on the frame it was read: its unit id, its role, its BWAPI
 * order, how far it stood from the plan's build tile and whether it was in build range.
 *
 * <p>A reading is a snapshot rather than a live unit, so a builder that has since died, been
 * re-roled or been handed another plan is still reported as it was when it held this one.
 */
@Getter
public final class BuilderReading {
    private final int unitId;
    private final UnitRole role;
    private final String order;
    private final Integer distancePx;
    private final Boolean inRange;

    /**
     * @param unitId the executor's unit id
     * @param role the executor's role, or null when no managed unit wraps it
     * @param order the executor's BWAPI order name
     * @param distancePx distance to the build tile's top-left corner, or null without a build tile
     * @param inRange whether a drone building the plan is within {@link BuilderStall#ARRIVAL_DISTANCE}
     *     of its move target, or null when the executor is not a drone walking to a build tile
     */
    public BuilderReading(int unitId, UnitRole role, String order, Integer distancePx, Boolean inRange) {
        this.unitId = unitId;
        this.role = role;
        this.order = order;
        this.distancePx = distancePx;
        this.inRange = inRange;
    }

    /**
     * Reads an executor now. The distance is the one builder_distance_px has always carried, to the
     * build tile's top-left corner. Build range is measured the way {@code ManagedUnit.build()}
     * measures arrival: the unit's distance to the centre of the building's footprint against
     * {@link BuilderStall#ARRIVAL_DISTANCE}.
     *
     * @param unit the executor
     * @param role the executor's role, or null when no managed unit wraps it
     * @param plan the plan it executes
     */
    public static BuilderReading of(Unit unit, UnitRole role, Plan plan) {
        TilePosition buildPosition = plan.getBuildPosition();
        Integer distance = buildPosition == null ? null : unit.getDistance(buildPosition.toPosition());
        Boolean inRange = null;
        if (buildPosition != null && plan.getType() == PlanType.BUILDING && unit.getType().isWorker()) {
            inRange = isInRange(unit.getDistance(PlanManager.siteMoveTarget(plan.getPlannedUnit(), buildPosition)));
        }
        return new BuilderReading(unit.getID(), role, unit.getOrder().toString(), distance, inRange);
    }

    /**
     * Whether a builder this far from its move target is close enough to issue the morph.
     *
     * @param distanceToMoveTarget the builder's distance to the centre of the building's footprint
     */
    public static boolean isInRange(int distanceToMoveTarget) {
        return distanceToMoveTarget <= BuilderStall.ARRIVAL_DISTANCE;
    }
}
