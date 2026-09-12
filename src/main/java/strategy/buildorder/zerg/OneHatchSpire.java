package strategy.buildorder.zerg;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.HatcheryCapacity;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OneHatchSpire, baseline ZvZ build.
 * <a href="https://liquipedia.net/starcraft/9_Pool_Speed_into_1_Hatch_Spire_(vs._Zerg)">Liquipedia</a>
 */
public class OneHatchSpire extends ZergBase {
    public OneHatchSpire() {
        super("1HatchSpire");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();

        final int gas = gameState.getResourceCount().availableGas();
        final int extractorCount = baseData.numExtractor();
        final int hatchCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hatchery, UnitType.Zerg_Lair);
        final int lairCount         = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        final int spireCount        = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spire);
        final int committedSpires   = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spire);
        final int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        final int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        final int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        final int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        boolean firstGas = shouldPlanFirstGas(extractorCount, gameState.canPlanExtractor());
        boolean anotherGas = shouldPlanAnotherGas(committedSpires, gameState.canPlanExtractor());
        boolean wantLair = gameState.canPlanLair() && lairCount < 1;
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && 
                                    zerglingCount > 5 && lairCount > 0;
        boolean wantFlyingCarapace = mutaCount > 6 && techProgression.canPlanFlyerDefense();
        boolean wantOverlordSpeed = needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed();


        boolean wantHatchery = behindOnHatchery(gameState)
                || HatcheryCapacity.isFloatingExpansion(gameState.isFloatingMinerals(), gameState.isEarlyRushed());

        boolean enemyHasSpire = gameState.enemyUnitCount(UnitType.Zerg_Spire) > 0;

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        if (wantHatchery) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
        }

        if (techProgression.canPlanPool() && droneCount > 8) {
            Plan poolPlan = this.planSpawningPool(gameState);
            plans.add(poolPlan);
            return plans;
        }

        if (wantLair) {
            Plan lairPlan = this.planLair(gameState);
            plans.add(lairPlan);
            return plans;
        }

        if (wantSpire) {
            Plan spirePlan = this.planSpire(gameState);
            plans.add(spirePlan);
            return plans;
        }

        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        if (wantFlyingCarapace) {
            Plan flyingCarapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Flyer_Carapace);
            plans.add(flyingCarapacePlan);
        }

        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        if (firstGas || anotherGas) {
            Plan extractorPlan = this.planExtractor(gameState);
            plans.add(extractorPlan);
        }

        final int desiredScourge = 2;
        boolean wantScourge = techProgression.isSpire() && scourgeCount < desiredScourge && mutaCount > 5 && enemyHasSpire;

        final int flexibleMutalisks =  Math.max(0, (gas - 300) / 100);
        final int desiredMutalisks = Math.min(11 + flexibleMutalisks, 40);
        boolean wantMutalisk = techProgression.isSpire() && mutaCount < desiredMutalisks;

        boolean wantZergling = zerglingCount < this.zerglingsNeeded(gameState);

        final int desiredDroneCount = 10 + ((hatchCount - 1) * 6);
        boolean wantDrone = droneCount < desiredDroneCount && gameState.canPlanDrone();

        for (UnitType unitType : unitsToPlan(wantScourge, wantMutalisk, wantZergling, wantDrone)) {
            plans.addAll(this.planUnits(gameState, unitType));
        }

        Plan surplusPlan = this.planMineralSurplusUnit(gameState);
        if (surplusPlan != null) {
            plans.add(surplusPlan);
        }

        return plans;
    }

    /**
     * Whether the build takes its first gas yet.
     *
     * <p>Reads the pool through canPlanExtractor, which opens once the pool is planned rather than
     * once it stands. The Lair needs a finished Extractor, so the gas sits on the Spire critical
     * path. The pool is still built first: the gate cannot open before the pool is planned, and a
     * plan carries the frame it was enqueued on as its priority.
     *
     * @param extractorCount Extractors standing or reserved by a queued plan
     * @param canPlanExtractor GameState's verdict: a geyser is free, the pool is planned or
     *     standing, and no all-in, rush or replan hold bars the request
     * @return true while the first Extractor should be queued
     */
    static boolean shouldPlanFirstGas(int extractorCount, boolean canPlanExtractor) {
        return extractorCount < 1 && canPlanExtractor;
    }

    /**
     * Whether another Extractor should be queued.
     *
     * <p>The Spire term is {@link info.Readiness#COMMITTED} because the question it answers is
     * whether the build has turned towards Mutalisks, not whether it can morph one yet. The Spire
     * is what the gas is for, and a Spire standing part-built settles that as firmly as a finished
     * one does. Reading finished Spires instead withholds the geyser for the whole Spire build,
     * which is the stretch the gas is meant to cover.
     *
     * @param committedSpires Spires standing, under construction, or claimed by a plan in flight
     * @param canPlanExtractor GameState's verdict: a geyser is free, the pool is planned or
     *     standing, and no all-in, rush or replan hold bars the request
     * @return true while another Extractor should be queued
     */
    static boolean shouldPlanAnotherGas(int committedSpires, boolean canPlanExtractor) {
        return committedSpires > 0 && canPlanExtractor;
    }

    static List<UnitType> unitsToPlan(boolean wantScourge, boolean wantMutalisk, boolean wantZergling, boolean wantDrone) {
        List<UnitType> unitTypes = new ArrayList<>();
        if (wantScourge) {
            unitTypes.add(UnitType.Zerg_Scourge);
        }
        if (wantMutalisk) {
            unitTypes.add(UnitType.Zerg_Mutalisk);
        }
        if (wantZergling) {
            unitTypes.add(UnitType.Zerg_Zergling);
        } else if (wantDrone) {
            unitTypes.add(UnitType.Zerg_Drone);
        }
        return unitTypes;
    }

    private List<Plan> planUnits(GameState gameState, UnitType unitType) {
        if (unitType == UnitType.Zerg_Scourge || unitType == UnitType.Zerg_Mutalisk) {
            return this.planAdvancedUnit(gameState, unitType);
        }
        return Collections.singletonList(this.planUnit(gameState, unitType));
    }

    @Override
    public boolean needLair() { 
        return true; 
    }
}
