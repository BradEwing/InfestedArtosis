package strategy.buildorder;

import bwapi.Race;
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
 * Liquipedia Overview
 * <p>
 * This build opens very similarly to a ZvT 3 Hatch Mutalisk build. It looks to get Scourge and/or Mutalisks out very quickly to control Protoss' early midgame Tech, allowing Zerg the freedom to power Drones through the midgame. It can sometimes be used as an aggressive strategy by catching the Protoss main off guard while it has very few Cannons and minimal air defense.
 * <a href="https://github.com/Cmccrave/McRave/blob/699ecf1b0f8581f318b467c74744027f3eb9b852/Source/McRave/Builds/Zerg/ZvP/ZvP.cpp#L245">McRave 3HatchMuta</a>
 * <a href="https://liquipedia.net/starcraft/3_Hatch_Spire_(vs._Protoss)">Liquipedia</a>
 *
 */
public class ThreeHatchMuta extends ProtossBase {

    public ThreeHatchMuta() {
        super("3HatchMuta");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        Time time = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int supply = gameState.getSupply();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int hatchCount        = gameState.ourUnitCount(UnitType.Zerg_Hatchery);
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount         = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int spireCount        = gameState.ourUnitCount(UnitType.Zerg_Spire);
        int hydraCount        = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int overlordCount     = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int enemyCorsairCount = gameState.enemyUnitCount(UnitType.Protoss_Corsair);
        int enemyObserverCount = gameState.enemyUnitCount(UnitType.Protoss_Observer);

        // Gas timing
        boolean firstGas = gameState.canPlanExtractor() && (time.greaterThan(new Time(2, 32)) || supply > 40) && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && (spireCount > 0 || droneCount >= 20);

        // Base timing
        boolean wantNatural  = plannedAndCurrentHatcheries < 2 && supply >= 24;
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && droneCount > 13 && techProgression.isSpawningPool();

        // Lair timing
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && time.greaterThan(new Time (3, 20));

        // Spire timing
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && supply >= 64 && lairCount >= 1 && droneCount >= 16;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;

        // Plan buildings

        // Defensive Structures
        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (gameState.basesNeedingSunken(desiredSunkenColonies).size() > 0) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        // Bases
        if (wantNatural || wantThird) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (firstGas || secondGas) {
            Plan extractorPlan = this.planExtractor(gameState);
            plans.add(extractorPlan);
        }

        if (techProgression.canPlanPool() && droneCount > 10) {
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

        // Plan Units

        final int desiredScourge = enemyCorsairCount + enemyObserverCount;
        if (techProgression.isSpire() && scourgeCount < desiredScourge) {
            for (int i = 0; i < desiredScourge - scourgeCount; i++) {
                Plan scourgePlan = this.planUnit(gameState, UnitType.Zerg_Scourge);
                plans.add(scourgePlan);
            }
            return plans;
        }

        final int desiredMutalisks = 9;
        if (techProgression.isSpire() && mutaCount < desiredMutalisks) {
            for (int i = 0; i < desiredMutalisks - mutaCount; i++) {
                Plan mutaliskPlan = this.planUnit(gameState, UnitType.Zerg_Mutalisk);
                plans.add(mutaliskPlan);
            }
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

        if (plans.isEmpty() && gameState.canPlanDrone()) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(zerglingPlan);
            return plans;
        }

        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        switch (race) {
            case Protoss:
            case Terran:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean needLair() { return true; }
}
