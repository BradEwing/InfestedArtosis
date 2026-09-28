package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.plan.Plan;
import macro.plan.PlanState;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 9 Pool Speed in the standard order: drones to 9, Spawning Pool at 9, drone, Extractor at 9,
 * Overlord at 8, drone, 6 zerglings the moment the pool finishes, then Metabolic Boost.
 *
 * <p>The opener hands over only once Metabolic Boost is queued and all six opening zerglings have
 * started, each plan holding its larva or already an egg. A zergling that is only queued can still
 * be held, on supply while the first Overlord is in its egg for one, and the terminal build order's
 * natural hatchery would then take the build-ahead slot and keep the zergling waiting until the
 * hatchery morphs. Until then the opener queues nothing past Metabolic Boost.
 */
public class NinePoolSpeed extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final int DRONE_TARGET = 9;

    static final int OPENING_ZERGLINGS = 6;

    private boolean overlordHoldReleased = false;

    private boolean speedQueued = false;

    private final List<Plan> openingZerglings = new ArrayList<>();

    public NinePoolSpeed() {
        super("9PoolSpeed");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        return openingDone(gameState.ourUnitCount(UnitType.Zerg_Zergling),
                unstartedOpeningZerglings(gameState),
                speedQueued || techProgression.isPlannedMetabolicBoost() || techProgression.isMetabolicBoost(),
                extractorDenied(
                        gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Extractor),
                        gameState.getBaseData().numExtractor(),
                        gameState.canPlanExtractor()));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forGame(gameState);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount     = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed     = gameState.getSupply();
        int poolCount      = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool);
        int committedPools = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool);
        int standingPools  = gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spawning_Pool);
        int extractorCount = gameState.getBaseData().numExtractor();
        int zerglingCount  = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (shouldPlanDrone(droneCount, gameState.canPlanOpeningDrone())) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && committedPools < 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (shouldPlanExtractor(extractorCount, standingPools, supplyUsed, gameState.canPlanExtractor())) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (shouldPlanOpeningZergling(poolCount, zerglingCount) && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            Plan zergling = planUnit(gameState, UnitType.Zerg_Zergling);
            openingZerglings.add(zergling);
            plans.add(zergling);
            return plans;
        }

        if (zerglingCount >= OPENING_ZERGLINGS && gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            speedQueued = true;
            return plans;
        }

        if (unstartedOpeningZerglings(gameState) > 0) {
            return plans;
        }

        if (shouldPlanZergling(poolCount, zerglingCount, this.zerglingsNeeded(gameState))) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        plans.addAll(planUnknownRaceMacro(gameState));
        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    @Override
    public boolean isOpener() {
        return true;
    }

    @Override
    protected int poolPriority(int enqueueFrame) {
        return SPAWNING_POOL_PRIORITY;
    }

    /**
     * Whether the opener queues one of its own drones: up to 9, counting planned drones, including
     * the one that replaces the pool's drone. The shared expected-worker ceiling does not apply, since
     * against Zerg it is 7 at one base and would leave the opener at 8 supply with no drone to
     * bring it to the Extractor's 9.
     *
     * @param droneCount drones living and planned
     * @param canPlanOpeningDrone whether the planned-worker limit allows another drone
     * @return true while the opener should queue a drone
     */
    static boolean shouldPlanDrone(int droneCount, boolean canPlanOpeningDrone) {
        return droneCount < DRONE_TARGET && canPlanOpeningDrone;
    }

    static boolean shouldPlanPool(int supplyUsed) {
        return supplyUsed >= POOL_SUPPLY;
    }

    /**
     * Whether the opening is finished: the six opening zerglings exist or are planned, none of
     * their plans is still waiting in the queue or on the schedule, and Metabolic Boost is queued,
     * researching or done, or cannot come because no Extractor can. The SCV rush reaction blocks
     * Extractors until 12 zerglings live while the opener stops short of that, and a stolen geyser
     * leaves none to take, so waiting on speed there would keep the opener from ever handing over.
     *
     * <p>A zergling plan that holds its larva, or whose larva is already an egg, has started: its
     * minerals are spent or reserved and its larva claimed, so nothing the terminal build order
     * queues can hold it. A cancelled plan is not waited on; it lowers the zergling count, and the
     * opener queues a replacement that it waits on instead.
     *
     * @param zerglingCount zerglings living and planned, two per plan
     * @param unstartedOpeningZerglings opening zergling plans still in the queue or on the schedule
     * @param speedCommitted whether Metabolic Boost is queued, researching or finished
     * @param extractorDenied whether no Extractor stands, none is reserved and none can be planned
     * @return true once the opener may hand over
     */
    static boolean openingDone(int zerglingCount, int unstartedOpeningZerglings, boolean speedCommitted,
                               boolean extractorDenied) {
        return zerglingCount >= OPENING_ZERGLINGS
                && unstartedOpeningZerglings == 0
                && (speedCommitted || extractorDenied);
    }

    private int unstartedOpeningZerglings(GameState gameState) {
        return unstartedPlans(openingZerglings,
                plan -> gameState.getProductionQueue().contains(plan) || gameState.getPlansScheduled().contains(plan));
    }

    /**
     * Counts the plans that have not started: still carried by the production queue or the
     * scheduled set, and in {@link PlanState#PLANNED} or {@link PlanState#SCHEDULE}. A plan that
     * has left both, whether it moved on to a larva or was dropped, is not counted, so a plan
     * removed without a state change cannot hold the count up.
     *
     * @param plans the plans to check
     * @param carried whether a plan is still in the production queue or the scheduled set
     * @return the number of plans still waiting to start
     */
    static int unstartedPlans(List<Plan> plans, Predicate<Plan> carried) {
        int unstarted = 0;
        for (Plan plan : plans) {
            PlanState state = plan.getState();
            if (carried.test(plan) && (state == PlanState.PLANNED || state == PlanState.SCHEDULE)) {
                unstarted += 1;
            }
        }
        return unstarted;
    }

    /**
     * Whether the opener's Extractor cannot come: none stands, none is reserved by a queued plan,
     * and none may be planned.
     *
     * @param standingExtractors Extractors finished or under construction
     * @param reservedExtractors Extractors standing or reserved by a queued plan
     * @param canPlanExtractor whether a new Extractor may be planned this frame
     * @return true while no Extractor can be expected
     */
    static boolean extractorDenied(int standingExtractors, int reservedExtractors, boolean canPlanExtractor) {
        return standingExtractors < 1 && reservedExtractors < 1 && !canPlanExtractor;
    }

    /**
     * Holds every Overlord until the Spawning Pool and the Extractor are both standing, which is
     * 8 of 9 supply: the Extractor takes the 9th drone after the pool's drone was replaced. The
     * supply planner queues the first Overlord at priority 1 on the frame the hold releases, so it
     * takes the next larva ahead of the drone that follows it.
     *
     * <p>Once the pool stands, an Extractor that is neither reserved nor plannable releases the
     * hold instead of waiting on it: the SCV rush reaction cancels Extractors and blocks new ones,
     * and a stolen geyser leaves none to reserve, so the Extractor may never stand.
     *
     * <p>Once released the hold stays released. Against an unknown race the opener never hands
     * over, and a hold that re-armed on later losses would stop all Overlord planning.
     *
     * @param gameState current game state
     * @return true while the supply planner must not queue an Overlord
     */
    @Override
    public boolean holdsOverlords(GameState gameState) {
        return latchOverlordHold(holdsOverlords(
                gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spawning_Pool),
                gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Extractor),
                gameState.getBaseData().numExtractor(),
                gameState.canPlanExtractor()));
    }

    /**
     * Records this frame's hold and keeps it released once it has released.
     *
     * @param held whether the opening conditions hold the Overlord this frame
     * @return true until the hold has released once
     */
    boolean latchOverlordHold(boolean held) {
        overlordHoldReleased = overlordHoldReleased || !held;
        return !overlordHoldReleased;
    }

    /**
     * Whether the supply planner must hold the first Overlord.
     *
     * @param standingPools Spawning Pools finished or under construction
     * @param standingExtractors Extractors finished or under construction
     * @param reservedExtractors Extractors standing or reserved by a queued plan
     * @param canPlanExtractor whether a new Extractor may be planned this frame
     * @return true until a Spawning Pool stands and an Extractor either stands or can no longer
     *     be expected
     */
    static boolean holdsOverlords(int standingPools, int standingExtractors, int reservedExtractors,
                                  boolean canPlanExtractor) {
        return standingPools < 1
                || standingExtractors < 1 && !extractorDenied(standingExtractors, reservedExtractors, canPlanExtractor);
    }

    /**
     * Whether the opener takes its gas: at 9 supply, once the pool is standing and its drone has
     * been replaced. A pool that is only queued has not consumed its drone, so supply still reads
     * 9 and a committed-pool gate would let the Extractor take the pool's replacement drone's slot.
     *
     * @param extractorCount Extractors standing or reserved by a queued plan
     * @param standingPools Spawning Pools under construction or finished
     * @param supplyUsed supply used, in BWAPI's doubled units
     * @param canPlanExtractor whether a geyser is free and the pool is planned or standing
     * @return true while the Extractor should be queued
     */
    static boolean shouldPlanExtractor(int extractorCount, int standingPools, int supplyUsed,
                                       boolean canPlanExtractor) {
        return extractorCount < 1 && standingPools > 0 && supplyUsed >= POOL_SUPPLY && canPlanExtractor;
    }

    /**
     * Whether another of the six opening zerglings is queued. They wait on a finished pool, so each
     * plan morphs as soon as it is scheduled rather than holding a larva through the pool build.
     *
     * @param poolCount Spawning Pools that have finished building
     * @param zerglingCount zerglings living and planned, two per plan
     * @return true until the six opening zerglings are planned
     */
    static boolean shouldPlanOpeningZergling(int poolCount, int zerglingCount) {
        return poolCount > 0 && zerglingCount < OPENING_ZERGLINGS;
    }

    /**
     * Whether the opener queues another zergling.
     *
     * <p>The pool term counts finished pools. A zergling plan created against a pool that is only
     * committed to still takes a larva and holds it in BUILDING, because the morph no-ops until it
     * is buildable, so the plan buys nothing and costs the larva and the minerals it reserves for
     * the rest of the pool build. The count it is compared against includes planned zerglings, two
     * per plan, so the branch would otherwise run away while the pool was still going up.
     *
     * @param poolCount Spawning Pools that have finished building
     * @param zerglingCount zerglings living and planned, two per plan
     * @param zerglingsNeeded the target the build order asks for
     * @return true while another zergling should be queued
     */
    static boolean shouldPlanZergling(int poolCount, int zerglingCount, int zerglingsNeeded) {
        return poolCount > 0 && zerglingCount <= zerglingsNeeded;
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
