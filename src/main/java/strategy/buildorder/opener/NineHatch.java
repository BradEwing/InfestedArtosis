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
 * Pool, holding every Overlord until those are started, and hands over to the matchup's build
 * orders once the pool is standing.
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
        return planSteps(gameState, gameState.ourUnitCount(UnitType.Zerg_Drone), gameState.getSupply(),
                plannedAndCurrentBases(gameState), dronesMade(gameState), gameState.getTechProgression().canPlanPool());
    }

    private static int plannedAndCurrentBases(GameState gameState) {
        return gameState.getPlannedHatcheries() + gameState.getBaseData().currentBaseCount();
    }

    private static int expansionsStarted(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        return gameState.hatcheriesUnderConstruction(false) + Math.max(0, baseData.currentBaseCount() - 1);
    }

    private static int standingPools(GameState gameState) {
        return gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spawning_Pool);
    }

    private static int dronesMade(GameState gameState) {
        return gameState.ourUnitCount(UnitType.Zerg_Drone) + expansionsStarted(gameState) + standingPools(gameState);
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
     * @param dronesMade drones living and planned, plus drones spent on the natural and the pool
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
     * @param dronesMade drones living and planned, plus drones spent on the natural and the pool
     * @return true while the drones, counting those spent on the natural and the pool, are short of 10
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
     * @param dronesMade drones living and planned, plus drones spent on the natural and the pool
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
        boolean naturalPending = plannedAndCurrentBases(gameState) >= BASES_WITH_NATURAL
                && expansionsStarted(gameState) == 0;
        return openerComplete(standingPools(gameState), naturalPending, dronesMade(gameState));
    }

    /**
     * Whether the opener has produced everything it will produce.
     *
     * <p>Complete once the pool is standing, the drones are back to 9 after the hatchery, and no
     * natural is still waiting on its builder. A natural that was never queued, because
     * planNewBase refused it, does not hold the opener. A natural that is queued but not started
     * does, so the matchup build order's supply and drones cannot outbid its 300 minerals.
     *
     * @param standingPools Spawning Pools finished or under construction
     * @param naturalPending whether a natural hatchery plan is in flight and its hatchery not started
     * @param dronesMade drones living and planned, plus drones spent on the natural and the pool
     * @return true once the opener should hand off
     */
    static boolean openerComplete(int standingPools, boolean naturalPending, int dronesMade) {
        return standingPools > 0 && !naturalPending && dronesMade >= DRONES_WITH_REPLACEMENT;
    }

    /**
     * Holds every Overlord until the opener is complete: the natural started, the drones back to
     * 9 after it, and the pool started. Each of those steps frees or fits in the supply the
     * starting Overlord and Hatchery give, so none waits on supply for long. An Overlord queued
     * earlier outranks the natural and takes the minerals it is saving for.
     *
     * @param gameState current game state
     * @return true until the opener is complete
     */
    @Override
    public boolean holdsOverlords(GameState gameState) {
        return !openerComplete(gameState);
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
