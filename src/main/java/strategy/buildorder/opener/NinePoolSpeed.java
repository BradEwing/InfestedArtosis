package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.plan.Plan;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NinePoolSpeed extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final int DRONE_TARGET = 9;

    private static final Time GAS_TIME = new Time(1, 4);

    public NinePoolSpeed() {
        super("9PoolSpeed");
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return !holdsOverlords(gameState);
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forRace(gameState.getOpponentRace());
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();
        Time gameTime = gameState.getGameTime();

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

        if (droneCount < 6 && gameState.canPlanDrone()) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (poolCount > 0 && zerglingCount < 3 && gameState.canPlanUnit(UnitType.Zerg_Zergling)) {
            plans.add(planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (shouldPlanExtractor(extractorCount, gameState.canPlanExtractor(), gameTime.greaterThan(GAS_TIME))) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
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
     * Holds every Overlord until 9 drones are made, the Spawning Pool is started, and 9 supply is
     * used. The opener is complete, and hands over, on the frame the hold releases.
     *
     * <p>Counts a Spawning Pool under construction, not only a finished one: everything the
     * terminal build order would queue next, the natural hatchery above all, is unreachable until
     * the opener hands over. The hand-over waits for the hold because the terminal build order
     * holds nothing: taking over while the pool is only planned, or before the pool's drone is
     * replaced and morphing, the supply planner would queue the first Overlord at priority 1 ahead
     * of the pool or of that drone.
     *
     * @param gameState current game state
     * @return true while the supply planner must not queue an Overlord
     */
    @Override
    public boolean holdsOverlords(GameState gameState) {
        return holdsOverlords(
                gameState.ourUnitCount(UnitType.Zerg_Drone),
                gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spawning_Pool),
                gameState.getSupply());
    }

    /**
     * Whether the supply planner must hold the first Overlord.
     *
     * <p>The drone count includes drones planned and morphing, so it reaches 9 on the frame the
     * replacement for the pool's drone is queued. The supply term keeps the hold on until that
     * drone's egg is morphing, which is when supply used counts it. The supply planner queues
     * the first Overlord at priority 1 on the frame the hold releases, so releasing while the
     * replacement drone is only planned would let the Overlord take the larva ahead of it. Holding
     * until the drone's build finishes instead would lengthen the block at 9 supply.
     *
     * @param droneCount drones living, morphing, and planned
     * @param standingPools Spawning Pools finished or under construction
     * @param supplyUsed supply used, in BWAPI's doubled units
     * @return true until 9 drones are made, a Spawning Pool is standing, and 9 supply is used
     */
    static boolean holdsOverlords(int droneCount, int standingPools, int supplyUsed) {
        return droneCount < DRONE_TARGET || standingPools < 1 || supplyUsed < POOL_SUPPLY;
    }

    /**
     * Whether the opener takes its gas yet.
     *
     * <p>Reads the pool through canPlanExtractor, which opens once the pool is planned. A gate on
     * a completed pool is the same expression as openerComplete, so against a known opponent race
     * the opener handed off on the frame its own gas branch first became true.
     *
     * @param extractorCount Extractors standing or reserved by a queued plan
     * @param canPlanExtractor whether a geyser is free and the pool is planned or standing
     * @param pastGasTime whether the game is past GAS_TIME
     * @return true while the Extractor should be queued
     */
    static boolean shouldPlanExtractor(int extractorCount, boolean canPlanExtractor, boolean pastGasTime) {
        return extractorCount < 1 && canPlanExtractor && pastGasTime;
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
