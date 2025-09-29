package strategy.buildorder.zerg;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import util.Time;

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

        final int gas = gameState.getResourceCount().availableGas();
        final int plannedHatcheries = gameState.getPlannedHatcheries();
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

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && zerglingCount > 5 && lairCount > 0;
        boolean wantFlyingCarapace = mutaCount > 6 && techProgression.canPlanFlyerDefense() && techProgression.getFlyerDefense() < 1;


        boolean floatingMinerals = gameState.getGameTime().greaterThan(new Time(5, 0)) && gameState.getResourceCount().availableMinerals() > ((plannedHatcheries + 1) * 350);
        boolean wantHatchery = behindOnHatchery(gameState) || floatingMinerals;

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

        if (firstGas || anotherGas) {
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

        final int flexibleMutalisks =  Math.max(0, (gas - 300) / 100);
        final int desiredMutalisks = Math.min(11 + flexibleMutalisks, 40);
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

        int desiredDroneCount = 10 + ((hatchCount - 1) * 6);
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
