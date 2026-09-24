package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Drones to 9, takes the natural hatchery, builds one drone to replace the one that became the
 * hatchery, then the Spawning Pool, and hands over to the matchup's build orders once the pool is
 * committed.
 *
 * <p>Not played against an unknown race: it is a hatchery-first opener, like 12Hatch and
 * 3HatchBeforePool, which are excluded there for the same reason.
 */
public class NineHatch extends BuildOrder {

    private static final int DRONE_TARGET = 9;

    private static final int HATCHERY_SUPPLY = 18;

    private static final int DRONES_WITH_REPLACEMENT = DRONE_TARGET + 1;

    private static final int BASES_WITH_NATURAL = 2;

    public NineHatch() {
        super("9Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Unknown;
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        BaseData baseData = gameState.getBaseData();
        TechProgression techProgression = gameState.getTechProgression();
        int baseCount = baseData.currentBaseCount();
        int plannedAndCurrentBases = gameState.getPlannedHatcheries() + baseCount;
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed = gameState.getSupply();
        int dronesSpentOnExpansions = gameState.hatcheriesUnderConstruction(false) + Math.max(0, baseCount - 1);
        int dronesMade = droneCount + dronesSpentOnExpansions;

        if (shouldPlanDrone(droneCount, plannedAndCurrentBases)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanHatchery(supplyUsed, plannedAndCurrentBases)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
            return plans;
        }

        if (shouldPlanReplacementDrone(plannedAndCurrentBases, dronesMade)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(dronesMade, techProgression.canPlanPool())) {
            plans.add(planSpawningPool(gameState));
        }

        return plans;
    }

    /**
     * Whether the opener queues another drone before its hatchery.
     *
     * @param droneCount drones living and planned
     * @param plannedAndCurrentBases bases standing or claimed by a hatchery plan in flight
     * @return true while the opener is still droning to its hatchery count
     */
    static boolean shouldPlanDrone(int droneCount, int plannedAndCurrentBases) {
        return droneCount < DRONE_TARGET && plannedAndCurrentBases < BASES_WITH_NATURAL;
    }

    /**
     * Whether the opener queues the natural hatchery.
     *
     * @param supplyUsed supply used now, in BWAPI's doubled units
     * @param plannedAndCurrentBases bases standing or claimed by a hatchery plan in flight
     * @return true once 9 drones are out and no natural is standing or planned
     */
    static boolean shouldPlanHatchery(int supplyUsed, int plannedAndCurrentBases) {
        return supplyUsed >= HATCHERY_SUPPLY && plannedAndCurrentBases < BASES_WITH_NATURAL;
    }

    /**
     * Whether the opener queues the drone that replaces the one spent on the natural.
     *
     * <p>Counts the drone that became the hatchery as still made, so the drone is requested once
     * whether the builder is still walking to the natural or has already morphed.
     *
     * @param plannedAndCurrentBases bases standing or claimed by a hatchery plan in flight
     * @param dronesMade drones living and planned, plus drones spent on expansion hatcheries
     * @return true while the natural is committed and its replacement drone is not
     */
    static boolean shouldPlanReplacementDrone(int plannedAndCurrentBases, int dronesMade) {
        return plannedAndCurrentBases >= BASES_WITH_NATURAL && dronesMade < DRONES_WITH_REPLACEMENT;
    }

    /**
     * Whether the opener queues its Spawning Pool.
     *
     * <p>Reads the drones made and the pool itself, never the hatchery, so a natural that is
     * still walking, morphing or held back by a reaction cannot hold the pool.
     *
     * @param dronesMade drones living and planned, plus drones spent on expansion hatcheries
     * @param canPlanPool whether no Spawning Pool is standing or already claimed by a plan
     * @return true once the replacement drone is committed and no pool is
     */
    static boolean shouldPlanPool(int dronesMade, boolean canPlanPool) {
        return dronesMade >= DRONES_WITH_REPLACEMENT && canPlanPool;
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return openerComplete(gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool));
    }

    /**
     * Whether the opener has produced everything it will produce.
     *
     * <p>The pool is the opener's last scripted act, and every transition target plans its own
     * pool through the same canPlanPool gate, so handing over on a committed pool leaves nothing
     * unbuilt.
     *
     * @param poolCount Spawning Pools standing, under construction, or claimed by a plan in flight
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int poolCount) {
        return poolCount > 0;
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    public boolean isOpener() {
        return true;
    }

    /**
     * False. The opener hands over before any tech unit exists, so it is never larva bound on
     * tech and never needs the shared macro hatchery.
     */
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return false;
    }
}
