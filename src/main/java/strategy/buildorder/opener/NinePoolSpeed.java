package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 9 Pool Speed in the standard order: drones to 9, Spawning Pool at 9, drone, Extractor at 9,
 * Overlord at 8, drone, 6 zerglings the moment the pool finishes, then Metabolic Boost.
 *
 * <p>The opener hands over only once all six zerglings and Metabolic Boost are queued, so the
 * terminal build order's natural hatchery cannot take the minerals the opening zerglings need.
 */
public class NinePoolSpeed extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final int DRONE_TARGET = 9;

    static final int OPENING_ZERGLINGS = 6;

    private boolean overlordHoldReleased = false;

    private boolean speedQueued = false;

    public NinePoolSpeed() {
        super("9PoolSpeed");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        return openingDone(gameState.ourUnitCount(UnitType.Zerg_Zergling),
                speedQueued || techProgression.isPlannedMetabolicBoost() || techProgression.isMetabolicBoost());
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int droneCount     = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int supplyUsed     = gameState.getSupply();
        int poolCount      = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool);
        int committedPools = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool);
        int extractorCount = gameState.getBaseData().numExtractor();
        int zerglingCount  = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        if (droneCount < DRONE_TARGET && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (shouldPlanPool(supplyUsed) && committedPools < 1 && techProgression.canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        if (shouldPlanExtractor(extractorCount, committedPools, supplyUsed, gameState.canPlanExtractor())) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (shouldPlanOpeningZergling(poolCount, zerglingCount) && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (zerglingCount >= OPENING_ZERGLINGS && gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            speedQueued = true;
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

    static boolean shouldPlanPool(int supplyUsed) {
        return supplyUsed >= POOL_SUPPLY;
    }

    /**
     * Whether the opening is finished: the six opening zerglings exist or are planned, and
     * Metabolic Boost is queued, researching or done.
     *
     * @param zerglingCount zerglings living and planned, two per plan
     * @param speedCommitted whether Metabolic Boost is queued, researching or finished
     * @return true once the opener may hand over
     */
    static boolean openingDone(int zerglingCount, boolean speedCommitted) {
        return zerglingCount >= OPENING_ZERGLINGS && speedCommitted;
    }

    /**
     * Holds every Overlord until the Spawning Pool and the Extractor are both standing, which is
     * 8 of 9 supply: the Extractor takes the 9th drone after the pool's drone was replaced. The
     * supply planner queues the first Overlord at priority 1 on the frame the hold releases, so it
     * takes the next larva ahead of the drone that follows it.
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
                gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Extractor)));
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
     * @return true until a Spawning Pool and an Extractor are both standing
     */
    static boolean holdsOverlords(int standingPools, int standingExtractors) {
        return standingPools < 1 || standingExtractors < 1;
    }

    /**
     * Whether the opener takes its gas: at 9 supply, once the pool is committed and its drone has
     * been replaced.
     *
     * @param extractorCount Extractors standing or reserved by a queued plan
     * @param committedPools Spawning Pools planned, under construction or finished
     * @param supplyUsed supply used, in BWAPI's doubled units
     * @param canPlanExtractor whether a geyser is free and the pool is planned or standing
     * @return true while the Extractor should be queued
     */
    static boolean shouldPlanExtractor(int extractorCount, int committedPools, int supplyUsed,
                                       boolean canPlanExtractor) {
        return extractorCount < 1 && committedPools > 0 && supplyUsed >= POOL_SUPPLY && canPlanExtractor;
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
