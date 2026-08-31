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
     * <p>Reactions, tech buildings, the extractor and the upgrades use lower fixed bands, so they
     * keep their precedence. This band polls before every frame-numbered plan still queued when a
     * tech building completes.
     */
    public static final int ADVANCED_UNIT_PRIORITY = 150;

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