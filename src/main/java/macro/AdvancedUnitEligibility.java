package macro;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.PlanBlocker;

/**
 * The one rule that decides whether a unit a tech building unlocks may be planned this frame.
 *
 * <p>The build order asks before creating the plan and the impossible-plan sweep asks before
 * deleting it, so a plan is never created on a frame the sweep would end. Every input is
 * invariant under queueing or cancelling a unit plan: the tech flags move with building plans and
 * the gatherer count moves with drones, so neither side can switch the other on.
 */
public final class AdvancedUnitEligibility {

    static final int MIN_GATHERERS = 4;

    private AdvancedUnitEligibility() {
    }

    /**
     * @param gatherers drones assigned to gather this frame
     * @return the first failing term, or NONE when the unit may be planned and scheduled
     */
    public static PlanBlocker blocker(UnitType unitType, TechProgression techProgression, int gatherers) {
        if (!hasTech(unitType, techProgression)) {
            return PlanBlocker.TECH_MISSING;
        }
        if (gatherers < MIN_GATHERERS) {
            return PlanBlocker.INSUFFICIENT_GATHERERS;
        }
        return PlanBlocker.NONE;
    }

    /**
     * True once the tech building or research that unlocks the unit is planned or complete.
     */
    static boolean hasTech(UnitType unitType, TechProgression techProgression) {
        switch (unitType) {
            case Zerg_Hydralisk:
                return techProgression.isPlannedDen() || techProgression.isHydraliskDen();
            case Zerg_Lurker:
                return techProgression.isPlannedLurker() || techProgression.isLurker();
            case Zerg_Mutalisk:
            case Zerg_Scourge:
                return techProgression.isPlannedSpire() || techProgression.isSpire();
            case Zerg_Ultralisk:
                return techProgression.isPlannedUltraliskCavern() || techProgression.isUltraliskCavern();
            case Zerg_Defiler:
                return techProgression.isPlannedDefilerMound() || techProgression.isDefilerMound();
            default:
                return false;
        }
    }
}
