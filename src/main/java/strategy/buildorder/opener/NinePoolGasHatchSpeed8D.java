package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.ExtractorTrick;
import macro.plan.Plan;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LingFloodHold;
import util.Time;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 9 Pool Gas Hatch Speed, 8 drones, against Zerg: drones to 9, Spawning Pool at 9, drone, the
 * extractor trick, Overlord, 3 zergling pairs, Extractor, a Hatchery in the main, 2 zergling pairs,
 * Metabolic Boost, then 35 zergling pairs while the drone count is held at 8.
 *
 * <p>The extractor trick is run by {@link ExtractorTrick}: the build arms it at 9 of 9 supply with
 * an Extractor and a Drone, and the Drone morphs on the supply the Extractor's drone freed before
 * the Extractor is cancelled, which ends the trick at 10 of 9 supply. Overlords are held until the
 * trick has settled, so the first Overlord follows it.
 *
 * <p>The Extractor mines {@link #GAS_TARGET} gas, the cost of Metabolic Boost, and every drone
 * then leaves gas for the rest of the opener. The Hatchery is a macro hatchery placed in the main.
 *
 * <p>The opener hands over once all {@link #TOTAL_ZERGLING_PLANS} zergling pairs are queued, once
 * the {@link LingFloodHold Zergling flood hold} stands, or at {@link #HAND_OVER_DEADLINE}, so a
 * starved Zergling count or the hold's Drone floor and home-kept army cannot keep the bot on a
 * Zergling-only build with no tech.
 */
public class NinePoolGasHatchSpeed8D extends BuildOrder {
    private static final int POOL_SUPPLY = 18;

    private static final int OPENING_DRONES = 9;

    static final int DRONE_CAP = 8;

    static final int FIRST_ZERGLING_PLANS = 3;

    static final int SECOND_ZERGLING_PLANS = 2;

    static final int FLOOD_ZERGLING_PLANS = 35;

    static final int TOTAL_ZERGLING_PLANS = FIRST_ZERGLING_PLANS + SECOND_ZERGLING_PLANS + FLOOD_ZERGLING_PLANS;

    static final int GAS_TARGET = 100;

    static final int MAX_QUEUED_ZERGLING_PLANS = 2;

    /**
     * The game time the opener hands over at even when Zergling pairs are still owed.
     */
    static final Time HAND_OVER_DEADLINE = new Time(8, 0);

    /**
     * The priority a drone that restores the cap is queued at: behind the emergency defense band
     * and the Spawning Pool, ahead of every Zergling pair and Metabolic Boost, which are queued at
     * their enqueue frame.
     */
    static final int CAP_DRONE_PRIORITY = EMERGENCY_DEFENSE_PRIORITY + 2;

    private int zerglingPlans = 0;

    private boolean macroHatcheryPlanned = false;

    private boolean speedQueued = false;

    private boolean gasTaken = false;

    private boolean overlordHoldReleased = false;

    public NinePoolGasHatchSpeed8D() {
        super("9PoolGasHatchSpeed8D");
    }

    @Override
    public boolean allowsBunkerEcon(GameState gameState) {
        return false;
    }

    @Override
    protected boolean openerComplete(GameState gameState) {
        return openerComplete(zerglingPlans, gameState.isLingFloodHold(), gameState.getGameTime());
    }

    /**
     * Whether the opener hands over: every Zergling pair is queued, the Zergling flood hold
     * stands, or {@link #HAND_OVER_DEADLINE} has passed.
     *
     * @param zerglingPlans zergling plans this opener has queued so far
     * @param lingFloodHold whether the Zergling flood hold stands
     * @param gameTime current game time
     * @return true when the opener is done
     */
    static boolean openerComplete(int zerglingPlans, boolean lingFloodHold, Time gameTime) {
        return zerglingPlans >= TOTAL_ZERGLING_PLANS || lingFloodHold || gameTime.greaterThan(HAND_OVER_DEADLINE);
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return OpenerTransitions.forGame(gameState);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        ExtractorTrick trick = gameState.getExtractorTrick();

        if (!trick.isSettled()) {
            if (trick.isIdle()) {
                plans.addAll(planOpening(gameState, trick));
            }
            return plans;
        }

        int usablePools = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spawning_Pool);

        if (shouldPlanCapDrone(macroHatcheryPlanned, dronesAliveOrComing(gameState),
                gameState.canPlanOpeningDrone())) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone, CAP_DRONE_PRIORITY));
            return plans;
        }

        if (zerglingPlans < FIRST_ZERGLING_PLANS) {
            if (shouldPlanZergling(usablePools, zerglingPlans, FIRST_ZERGLING_PLANS, 0)) {
                plans.add(planZergling(gameState));
            }
            return plans;
        }

        if (shouldPlanExtractor(gasTaken, gameState.getBaseData().numExtractor(), gameState.canPlanExtractor())) {
            plans.add(planExtractor(gameState));
            return plans;
        }

        if (!macroHatcheryPlanned) {
            Plan hatchery = planMacroHatcheryAt(gameState, gameState.getBaseData().getMainBase());
            if (hatchery != null) {
                macroHatcheryPlanned = true;
                plans.add(hatchery);
                return plans;
            }
        }

        if (shouldPlanZergling(usablePools, zerglingPlans, FIRST_ZERGLING_PLANS + SECOND_ZERGLING_PLANS, 0)) {
            plans.add(planZergling(gameState));
            return plans;
        }

        if (!speedQueued && gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            speedQueued = true;
            return plans;
        }

        if (shouldPlanZergling(usablePools, zerglingPlans, TOTAL_ZERGLING_PLANS,
                gameState.queuedUnitPlanCount(UnitType.Zerg_Zergling))) {
            plans.add(planZergling(gameState));
        }

        return plans;
    }

    /**
     * Drones to 9, the Spawning Pool at 9, the drone that replaces the pool's drone, then the
     * extractor trick once supply is full.
     */
    private List<Plan> planOpening(GameState gameState, ExtractorTrick trick) {
        List<Plan> plans = new ArrayList<>();
        int supplyUsed = gameState.getSupply();
        int committedPools = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spawning_Pool);
        int standingPools = gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spawning_Pool);

        if (shouldPlanOpeningDrone(gameState.ourUnitCount(UnitType.Zerg_Drone), gameState.canPlanOpeningDrone())) {
            plans.add(planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (supplyUsed >= POOL_SUPPLY && committedPools < 1 && gameState.getTechProgression().canPlanPool()) {
            plans.add(planSpawningPool(gameState));
            return plans;
        }

        switch (trickStart(standingPools, supplyUsed, gameState.getSelf().supplyTotal(),
                gameState.canPlanExtractor())) {
            case ARM:
                int frame = gameState.getGameTime().getFrames();
                Plan extractor = planExtractor(gameState);
                Plan drone = planUnit(gameState, UnitType.Zerg_Drone, frame + 1);
                trick.arm(extractor, drone, frame);
                plans.add(extractor);
                plans.add(drone);
                break;
            case SKIP:
                trick.skip();
                break;
            default:
                break;
        }
        return plans;
    }

    private Plan planZergling(GameState gameState) {
        zerglingPlans += 1;
        return planUnit(gameState, UnitType.Zerg_Zergling);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Zerg;
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
     * Holds every Overlord until the extractor trick has settled, so the first Overlord follows
     * the trick rather than taking the larva and minerals it needs. Once released the hold stays
     * released.
     */
    @Override
    public boolean holdsOverlords(GameState gameState) {
        overlordHoldReleased = overlordHoldReleased || gameState.getExtractorTrick().isSettled();
        return !overlordHoldReleased;
    }

    /**
     * True once {@link #GAS_TARGET} gas has been gathered. The hold lasts while this build is
     * active, so the build that follows it mines gas as usual.
     */
    @Override
    public boolean holdsGasHarvesting(GameState gameState) {
        gasTaken = gasTaken || gameState.getSelf().gatheredGas() >= GAS_TARGET;
        return gasTaken;
    }

    /**
     * False. The opener hands over before any tech unit exists, so it is never larva bound on
     * tech and never needs the shared macro hatchery.
     */
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return false;
    }

    /** What the opening does about the extractor trick this frame. */
    enum TrickStart {
        WAIT,
        ARM,
        SKIP
    }

    /**
     * Whether the opening arms the extractor trick. It waits for a standing Spawning Pool and for
     * supply to fill with the pool's replacement drone, then arms the trick if a geyser can be
     * claimed. Supply beyond the Hatchery and the first Overlord means the trick frees nothing a
     * drone needs, and a geyser that cannot be claimed leaves nothing to start, so both skip it.
     *
     * @param standingPools Spawning Pools finished or under construction
     * @param supplyUsed supply used, in BWAPI's doubled units
     * @param supplyTotal supply provided, in BWAPI's doubled units
     * @param canPlanExtractor whether an Extractor may be planned this frame
     * @return the step to take
     */
    static TrickStart trickStart(int standingPools, int supplyUsed, int supplyTotal, boolean canPlanExtractor) {
        if (standingPools < 1) {
            return TrickStart.WAIT;
        }
        if (supplyTotal > POOL_SUPPLY) {
            return TrickStart.SKIP;
        }
        if (supplyUsed < supplyTotal) {
            return TrickStart.WAIT;
        }
        return canPlanExtractor ? TrickStart.ARM : TrickStart.SKIP;
    }

    /**
     * Whether the opening queues one of its drones: up to 9, counting planned drones, including
     * the one that replaces the pool's drone.
     *
     * @param droneCount drones living and planned
     * @param canPlanOpeningDrone whether the planned-worker limit allows another drone
     * @return true while the opening should queue a drone
     */
    static boolean shouldPlanOpeningDrone(int droneCount, boolean canPlanOpeningDrone) {
        return droneCount < OPENING_DRONES && canPlanOpeningDrone;
    }

    /**
     * Whether a lost drone is replaced. The cap holds from the frame the Hatchery is queued, whose
     * drone brings the count down to {@link #DRONE_CAP}; before that the scripted steps set the count.
     *
     * @param macroHatcheryPlanned whether the Hatchery has been queued
     * @param drones drones alive, in an egg or planned
     * @param canPlanDrone whether the planned-worker limit allows another drone
     * @return true when a drone should be queued
     */
    static boolean shouldPlanCapDrone(boolean macroHatcheryPlanned, int drones, boolean canPlanDrone) {
        return macroHatcheryPlanned && drones < DRONE_CAP && canPlanDrone;
    }

    /**
     * Whether another zergling pair is queued toward a step's running total. Zerglings wait on a
     * finished Spawning Pool, so a plan does not hold a larva through the pool build.
     *
     * @param usablePools Spawning Pools that have finished building
     * @param zerglingPlans zergling plans this opener has queued so far
     * @param target the running total of zergling plans the current step ends on
     * @param queuedZerglingPlans zergling plans still waiting in the production queue
     * @return true while the step should queue another pair
     */
    static boolean shouldPlanZergling(int usablePools, int zerglingPlans, int target, int queuedZerglingPlans) {
        return usablePools > 0 && zerglingPlans < target && queuedZerglingPlans < MAX_QUEUED_ZERGLING_PLANS;
    }

    /**
     * Whether the real Extractor is queued: gas has not been taken yet, no geyser is claimed, and
     * one may be claimed.
     *
     * @param gasTaken whether {@link #GAS_TARGET} gas has been gathered
     * @param reservedExtractors Extractors standing or reserved by a queued plan
     * @param canPlanExtractor whether an Extractor may be planned this frame
     * @return true when the Extractor should be queued
     */
    static boolean shouldPlanExtractor(boolean gasTaken, int reservedExtractors, boolean canPlanExtractor) {
        return !gasTaken && reservedExtractors < 1 && canPlanExtractor;
    }

    /**
     * Drones alive, in an egg, or planned and not yet in an egg. Counted from the units rather
     * than the running unit counts, so a drone returned by a cancelled Extractor is seen.
     */
    private static int dronesAliveOrComing(GameState gameState) {
        return countDrones(gameState.getSelf().getUnits())
                + gameState.queuedUnitPlanCount(UnitType.Zerg_Drone)
                + countDronePlans(gameState.getPlansScheduled())
                + countDronePlans(gameState.getPlansBuilding());
    }

    private static int countDrones(List<Unit> units) {
        int drones = 0;
        for (Unit unit : units) {
            if (unit.getType() == UnitType.Zerg_Drone
                    || unit.getType() == UnitType.Zerg_Egg && unit.getBuildType() == UnitType.Zerg_Drone) {
                drones += 1;
            }
        }
        return drones;
    }

    private static int countDronePlans(Collection<Plan> plans) {
        int drones = 0;
        for (Plan plan : plans) {
            if (plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == UnitType.Zerg_Drone) {
                drones += 1;
            }
        }
        return drones;
    }
}
