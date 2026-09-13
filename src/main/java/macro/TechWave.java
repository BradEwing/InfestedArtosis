package macro;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;

/**
 * The first wave of units a tech building unlocks, pre-positioned while that building finishes.
 *
 * <p>Inside {@link #WINDOW_FRAMES} of the building's completion, production stops spending the
 * larva, minerals and gas the wave needs, so {@link #WAVE_SIZE} eggs can be issued the moment the
 * building completes. Supply is planned one Overlord build time earlier, because an Overlord still
 * in its egg at completion gives the wave nothing.
 *
 * <p>A wave is recomputed from the map every frame and reserves nothing in ResourceCount. It
 * holds only plans that have not been scheduled yet, so it never displaces a plan, and once the
 * building completes, dies, or a gate fails, the hold is gone on the next frame with nothing left
 * to release.
 *
 * <p>Only the Spire has a wave. A Lurker, Guardian or Devourer wave has no structure under
 * construction to time against, and a Hydralisk Den, Ultralisk Cavern or Defiler Mound wave is
 * left out until a ground-safety gate covers the matchups those buildings are built in.
 */
public final class TechWave {

    static final int WINDOW_FRAMES = 600;

    static final int WAVE_SIZE = 3;

    /** The zergling lead ZergBase.matchupSunkens already treats as a ground threat. */
    static final int ZERGLING_THREAT_MARGIN = 3;

    private final UnitType unit;

    private final int framesToCompletion;

    TechWave(UnitType unit, int framesToCompletion) {
        this.unit = unit;
        this.framesToCompletion = framesToCompletion;
    }

    /**
     * The unit a tech building's first wave is made of.
     *
     * @param prerequisite a structure under construction
     * @return the wave unit, or null when the structure has no wave
     */
    static UnitType waveUnit(UnitType prerequisite) {
        if (prerequisite == UnitType.Zerg_Spire) {
            return UnitType.Zerg_Mutalisk;
        }
        return null;
    }

    /**
     * The wave a structure under construction is building towards, or null when none applies.
     *
     * @param prerequisite the structure under construction
     * @param framesToCompletion frames left on its construction
     * @param alreadyUnlocked a finished structure of the same type already unlocked the wave unit
     * @param rushReactionActive a rush or larva deadlock reaction currently owns production
     * @param groundSafe no ground threat stands at our bases
     * @return the pending wave, or null
     */
    static TechWave pending(
            UnitType prerequisite,
            int framesToCompletion,
            boolean alreadyUnlocked,
            boolean rushReactionActive,
            boolean groundSafe) {
        UnitType unit = waveUnit(prerequisite);
        if (unit == null || alreadyUnlocked || rushReactionActive || !groundSafe) {
            return null;
        }
        if (framesToCompletion > supplyHorizonFrames()) {
            return null;
        }
        return new TechWave(unit, Math.max(0, framesToCompletion));
    }

    /**
     * True when holding larva and bank would not leave the bases open to a ground attack.
     *
     * @param ourZerglings our living zerglings
     * @param enemyZerglings enemy zerglings we know of
     * @param enemyGroundAtBases visible enemy mobile ground combat units at our bases
     * @return true when the wave may hold production
     */
    static boolean isGroundSafe(int ourZerglings, int enemyZerglings, int enemyGroundAtBases) {
        return enemyGroundAtBases == 0 && enemyZerglings < ourZerglings + ZERGLING_THREAT_MARGIN;
    }

    private static int supplyHorizonFrames() {
        return WINDOW_FRAMES + UnitType.Zerg_Overlord.buildTime();
    }

    UnitType getUnit() {
        return unit;
    }

    /** True inside the window, where the larva and bank holds apply. */
    boolean holdsProduction() {
        return framesToCompletion <= WINDOW_FRAMES;
    }

    /** Larva held back for the wave; a wave morphed from an existing unit holds none. */
    int larvaReserve() {
        return unit.whatBuilds().getKey() == UnitType.Zerg_Larva ? WAVE_SIZE : 0;
    }

    int mineralBank() {
        return WAVE_SIZE * unit.mineralPrice();
    }

    int gasBank() {
        return WAVE_SIZE * unit.gasPrice();
    }

    int supplyNeeded() {
        return WAVE_SIZE * SupplyCapacity.morphSupplyCost(unit);
    }

    /**
     * True when the wave's supply would not be standing by completion without another Overlord.
     *
     * @param freeSupply raw supply total less raw supply used
     * @param supplyInFlight raw supply of Overlords queued, scheduled or in an egg
     * @return true when an Overlord has to be queued now
     */
    boolean needsOverlord(int freeSupply, int supplyInFlight) {
        return freeSupply + supplyInFlight < supplyNeeded();
    }

    /**
     * Whether a plan not yet scheduled would spend what the wave is holding.
     *
     * <p>Overlords are exempt so the wave's own supply is never held up, as are the wave unit and
     * emergency defence.
     *
     * @param plan the plan being scheduled
     * @param freeLarva larva not reserved by a scheduled plan
     * @param availableMinerals minerals mined and not reserved
     * @param availableGas gas mined and not reserved
     * @return TECH_WAVE_RESERVE when the plan would breach the hold, otherwise NONE
     */
    PlanBlocker blocker(Plan plan, int freeLarva, int availableMinerals, int availableGas) {
        if (!holdsProduction() || isExempt(plan)) {
            return PlanBlocker.NONE;
        }
        if (takesLarva(plan) && freeLarva - 1 < larvaReserve()) {
            return PlanBlocker.TECH_WAVE_RESERVE;
        }
        if (plan.mineralPrice() > 0 && availableMinerals - plan.mineralPrice() < mineralBank()) {
            return PlanBlocker.TECH_WAVE_RESERVE;
        }
        if (plan.gasPrice() > 0 && availableGas - plan.gasPrice() < gasBank()) {
            return PlanBlocker.TECH_WAVE_RESERVE;
        }
        return PlanBlocker.NONE;
    }

    private boolean isExempt(Plan plan) {
        if (plan.getPriority() <= BuildOrder.EMERGENCY_DEFENSE_PRIORITY) {
            return true;
        }
        if (plan.getType() != PlanType.UNIT) {
            return false;
        }
        UnitType planned = plan.getPlannedUnit();
        return planned == UnitType.Zerg_Overlord || planned == unit;
    }

    private static boolean takesLarva(Plan plan) {
        return plan.getType() == PlanType.UNIT && plan.getPlannedUnit().whatBuilds().getKey() == UnitType.Zerg_Larva;
    }
}
