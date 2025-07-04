package strategy.buildorder.zerg;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * OneHatchSpire, baseline ZvZ build.
 * <a href="https://liquipedia.net/starcraft/9_Pool_Speed_into_1_Hatch_Spire_(vs._Zerg)">Liquipedia</a>
 */
public class OneHatchSpire extends ZergBase{
    public OneHatchSpire() {
        super("1HatchSpire");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();

        int extractorCount = baseData.numExtractor();
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery) + gameState.ourUnitCount(UnitType.Zerg_Lair);
        int lairCount         = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int spireCount        = gameState.ourUnitCount(UnitType.Zerg_Spire);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean wantLair = gameState.canPlanLair() && lairCount < 1;
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && zerglingCount > 5 && lairCount > 0;
        boolean wantFlyingCarapace = mutaCount > 6 && techProgression.canPlanFlyerDefense() && techProgression.getFlyerDefense() < 1;

        boolean wantHatchery = behindOnResourceDepot(gameState);

        if (wantHatchery) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans; // Prioritize catching up on bases
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

        // Plan Upgrades
        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        if (wantFlyingCarapace) {
            Plan flyingCarapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Flyer_Carapace);
            plans.add(flyingCarapacePlan);
        }

        if (firstGas) {
            Plan extractorPlan = this.planExtractor(gameState);
            plans.add(extractorPlan);
        }

        // Plan Units
        final int desiredScourge = 2;
        if (techProgression.isSpire() && scourgeCount < desiredScourge && mutaCount > 5) {
            for (int i = 0; i < desiredScourge - scourgeCount; i++) {
                Plan scourgePlan = this.planUnit(gameState, UnitType.Zerg_Scourge);
                plans.add(scourgePlan);
            }
            return plans;
        }

        final int desiredMutalisks = 11;
        if (techProgression.isSpire() && mutaCount < desiredMutalisks) {
            Plan mutaliskPlan = this.planUnit(gameState, UnitType.Zerg_Mutalisk);
            plans.add(mutaliskPlan);
            return plans;
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (zerglingCount < desiredZerglings) {
            for (int i = 0; i < desiredZerglings - zerglingCount; i++) {
                Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
                plans.add(zerglingPlan);
            }
            return plans;
        }

        int desiredDroneCount = 9 + ((1 - hatchCount) * 6);
        if (droneCount < desiredDroneCount && gameState.canPlanDrone()) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }



        return plans;
    }

    @Override
    public boolean needLair() { return true; }
}
