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
 * Drones to 9, takes the natural hatchery, drones back to 9 after the hatchery, then the Spawning
 * Pool, and hands over to the matchup's build orders once the pool is committed.
 *
 * <p>Not played against an unknown race: it is a hatchery-first opener, like 12Hatch and
 * 3HatchBeforePool, which are excluded there for the same reason.
 */
public class NineHatch extends BuildOrder {

    private static final int DRONE_TARGET = 9;

    private static final int HATCHERY_SUPPLY = 18;

    private static final int DRONES_WITH_REPLACEMENT = DRONE_TARGET + 1;

    private static final int BASES_WITH_NATURAL = 2;

    private static final int POOL_PRIORITY_OFFSET = 1;

    public NineHatch() {
        super("9Hatch");
    }

    @Override
    public boolean playsRace(Race race) {
        return race != Race.Unknown;
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int plannedAndCurrentBases = gameState.getPlannedHatcheries() + baseCount;
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int dronesSpentOnExpansions = gameState.hatcheriesUnderConstruction(false) + Math.max(0, baseCount - 1);
        return planSteps(gameState, droneCount, gameState.getSupply(), plannedAndCurrentBases,
                droneCount + dronesSpentOnExpansions, gameState.getTechProgression().canPlanPool());
    }

    /**
     * Walks the opener's steps for one frame.
     *
     * <p>Only the drone step before the hatchery ends the pass. A natural that cannot be queued
     * this frame, whether held by the expansion backoff, a lost builder, a reaction or the enqueue
     * cooldown, does not stop the drones back to 9 or the pool: the pass falls through to them,
     * and the natural is asked for again on every later pass while the opener is active.
     *
     * @param gameState current game state, passed through to the plan factories only
     * @param droneCount drones living and planned
     * @param supplyUsed supply used now, in BWAPI's doubled units
     * @param plannedAndCurrentBases bases standing or claimed by a hatchery plan in flight
     * @param dronesMade drones living and planned, plus drones spent on expansion hatcheries
     * @param canPlanPool whether no Spawning Pool is standing or already claimed by a plan
     * @return the plans for this frame
     */
    List<Plan> planSteps(GameState gameState, int droneCount, int supplyUsed, int plannedAndCurrentBases,
                         int dronesMade, boolean canPlanPool) {
        List<Plan> plans = new ArrayList<>();

        if (shouldPlanDrone(droneCount, plannedAndCurrentBases)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanHatchery(supplyUsed, plannedAndCurrentBases)) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
        }

        int dronesAfterThisPass = dronesMade;
        if (shouldDroneBackToNine(hatcheryStepReached(supplyUsed, plannedAndCurrentBases), dronesMade)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            dronesAfterThisPass += 1;
        }

        if (shouldPlanPool(dronesAfterThisPass, canPlanPool)) {
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
     * Whether the opener has reached its hatchery step, whether or not the natural could be
     * queued.
     *
     * @param supplyUsed supply used now, in BWAPI's doubled units
     * @param plannedAndCurrentBases bases standing or claimed by a hatchery plan in flight
     * @return true once 9 drones are out or the natural is committed
     */
    static boolean hatcheryStepReached(int supplyUsed, int plannedAndCurrentBases) {
        return supplyUsed >= HATCHERY_SUPPLY || plannedAndCurrentBases >= BASES_WITH_NATURAL;
    }

    /**
     * Whether the opener queues a drone to bring its drones back to 9 after the hatchery.
     *
     * <p>The drone the natural takes counts as made, both while it walks and once it has morphed,
     * so in a game with no drone losses this is the one replacement drone. A drone lost before
     * the pool drops the count again and is replaced as well.
     *
     * @param hatcheryStepReached whether the opener has reached its hatchery step
     * @param dronesMade drones living and planned, plus drones spent on expansion hatcheries
     * @return true while the drones, counting the natural's, are short of 10
     */
    static boolean shouldDroneBackToNine(boolean hatcheryStepReached, int dronesMade) {
        return hatcheryStepReached && dronesMade < DRONES_WITH_REPLACEMENT;
    }

    /**
     * Whether the opener queues its Spawning Pool.
     *
     * <p>Reads the drones made and the pool itself, never the hatchery, so a natural that is
     * still walking, morphing, or not queueable this frame cannot hold the pool.
     *
     * @param dronesMade drones living and planned, plus drones spent on expansion hatcheries
     * @param canPlanPool whether no Spawning Pool is standing or already claimed by a plan
     * @return true once the drones are back to 9 after the hatchery and no pool is committed
     */
    static boolean shouldPlanPool(int dronesMade, boolean canPlanPool) {
        return dronesMade >= DRONES_WITH_REPLACEMENT && canPlanPool;
    }

    /**
     * Priority for the Spawning Pool, one behind a hatchery or drone queued on the same frame, so
     * both are served first.
     *
     * @param enqueueFrame the frame the plan is created on
     * @return the plan priority
     */
    @Override
    protected int poolPriority(int enqueueFrame) {
        return enqueueFrame + POOL_PRIORITY_OFFSET;
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
