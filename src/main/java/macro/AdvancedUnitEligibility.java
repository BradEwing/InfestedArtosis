package macro;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.PlanBlocker;

/**
 * Decides whether a unit a tech building unlocks may be planned this frame. The build order
 * asks before creating the plan and the sweep asks before deleting it, so both agree.
 */
public final class AdvancedUnitEligibility {

    public static final int MIN_GATHERERS = 4;

    private AdvancedUnitEligibility() {
    }

    public static PlanBlocker blocker(UnitType unitType, TechProgression techProgression, int gatherers) {
        if (!hasTech(unitType, techProgression)) {
            return PlanBlocker.TECH_MISSING;
        }
        if (gatherers < MIN_GATHERERS) {
            return PlanBlocker.INSUFFICIENT_GATHERERS;
        }
        return PlanBlocker.NONE;
    }

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
