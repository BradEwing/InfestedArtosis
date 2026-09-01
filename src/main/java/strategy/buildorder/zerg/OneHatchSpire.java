package strategy.buildorder.zerg;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
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
        final int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery) + gameState.ourUnitCount(UnitType.Zerg_Lair);
        final int lairCount         = gameState.ourUnitCount(UnitType.Zerg_Lair);
        final int spireCount        = gameState.ourUnitCount(UnitType.Zerg_Spire);
        final int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        final int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        final int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        final int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean anotherGas = gameState.canPlanExtractor() && spireCount > 0;
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

        return plans;
    }

    /**
     * Orders the units this frame derives. Spire units never displace the zergling and drone
     * backlog: a queued Spire unit yields no plan, and ending the pass there stalled the backlog.
     */
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
