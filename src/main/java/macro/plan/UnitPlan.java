package macro.plan;

import bwapi.UnitType;
import lombok.Getter;
import lombok.Setter;

public class UnitPlan extends Plan {

    /**
     * Priority band for a unit that a tech building unlocks partway through the game.
     *
     * <p>A unit plan otherwise carries the frame it was derived on, and the lower priority polls
     * first. A unit that a late tech building unlocks therefore enters behind the plans derived
     * while it was waiting.
     *
     * <p>Reactions, tech buildings and the upgrades use lower fixed bands, so they keep their
     * precedence. Expansion hatcheries and the extractor carry the frame they were derived on and
     * sequence with the build order. This band polls before every frame-numbered plan still queued
     * when a tech building completes.
     */
    public static final int ADVANCED_UNIT_PRIORITY = 150;

    /**
     * Priority of a Drone queued by an open {@link macro.DroneRound}, ahead of the advanced unit
     * band. Two below it, so it never ties with the Overlord a pending tech wave queues one below
     * it. A round that closes returns its queued Drones to the frame they are demoted on.
     */
    public static final int DRONE_ROUND_PRIORITY = ADVANCED_UNIT_PRIORITY - 2;

    @Getter @Setter
    private UnitType plannedUnit;

    public UnitPlan(UnitType unitType, int priority) {
        super(priority);
        this.plannedUnit = unitType;
    }

    @Override
    public PlanType getType() {
        return PlanType.UNIT;
    }

    @Override
    public String getName() {
        return plannedUnit.toString();
    }

    @Override
    public int mineralPrice() {
        return plannedUnit.mineralPrice();
    }

    @Override
    public int gasPrice() {
        return plannedUnit.gasPrice();
    }
}