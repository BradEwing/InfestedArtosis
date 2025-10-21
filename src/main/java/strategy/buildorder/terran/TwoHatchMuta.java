package strategy.buildorder.terran;

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
 * Liquipedia Overview: The 2 Hatch Muta build can be a useful variation as many Terrans are comfortable countering
 * standard 3 Hatch Muta timings. However, this build requires excellent Mutalisk micro so you can effectively pick off
 * Marines and deal damage to your opponent. When playing a 2 Hatch, regardless of the ultimate variation, aggression
 * is key. 2 Hatch Mutas aim to take complete map control save for Terran's one timing push before your Hive tech is out.
 * If map control is lost due to loss of units, generally the Zerg has lost.
 *
 * @see <a href="https://liquipedia.net/starcraft/2_Hatch_Muta_(vs._Terran)">Liquipedia</a>
 */
public class TwoHatchMuta extends TerranBase {
    public TwoHatchMuta() {
        super("2HatchMuta");
    }

    private boolean startedFlyerUpgrade = false;

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
        int enemyVessel = gameState.enemyUnitCount(UnitType.Terran_Science_Vessel);
        int enemyDropship = gameState.enemyUnitCount(UnitType.Terran_Dropship);
        int enemyValkyrie = gameState.enemyUnitCount(UnitType.Terran_Valkyrie);
        int enemyWraith = gameState.enemyUnitCount(UnitType.Terran_Wraith);

        // Gas timing
        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && lairCount > 0;

        // Check for floating resources (follows ThreeHatchLurker pattern)
        boolean floatingMinerals = gameState.getGameTime().greaterThan(new Time(5, 0)) &&
                gameState.getResourceCount().availableMinerals() > ((plannedHatcheries + 1) * 350);

        // Base timing
        boolean wantNatural  = plannedAndCurrentHatcheries < 2 && droneCount >= 12;
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && spireCount > 0 && mutaCount > 5;
        boolean wantBaseAdvantage = behindOnBases(gameState) || floatingMinerals;

        // Lair timing
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;

        // Spire timing
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1 && droneCount >= 16;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;
        boolean wantFlyingAttack = mutaCount > 6 && techProgression.canPlanFlyerAttack() && techProgression.getFlyerAttack() < 1 && !startedFlyerUpgrade;
        boolean wantOverlordSpeed = needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed();

        // Plan buildings

        // Defensive Structures
        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        // Bases
        if (wantNatural || wantThird || wantBaseAdvantage) {
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


        if (spireCount > 1 && overlordCount < 4) {
            Plan overlordPlan = this.planUnit(gameState, UnitType.Zerg_Overlord);
            plans.add(overlordPlan);
            return plans;
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

        if (wantFlyingAttack) {
            // TODO: Generalize for all +3 unit upgrades
            startedFlyerUpgrade = true;
            Plan flyingCarapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Flyer_Attacks);
            plans.add(flyingCarapacePlan);
        }

        // Plan Overlord Speed
        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        // Plan Units
        final int desiredScourge = enemyVessel + enemyDropship + enemyValkyrie + enemyWraith;
        if (techProgression.isSpire() && scourgeCount < desiredScourge) {
            for (int i = 0; i < desiredScourge - scourgeCount; i++) {
                Plan scourgePlan = this.planUnit(gameState, UnitType.Zerg_Scourge);
                plans.add(scourgePlan);
            }
            return plans;
        }

        final int desiredMutalisks = desiredMutalisks(gameState);
        if (techProgression.isSpire() && mutaCount < desiredMutalisks) {
            Plan mutaliskPlan = this.planUnit(gameState, UnitType.Zerg_Mutalisk);
            plans.add(mutaliskPlan);
            return plans;
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (zerglingCount < desiredZerglings) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            plans.add(zerglingPlan);
            return plans;
        }

        int desiredDrones = this.dronesNeeded(gameState);
        if (plans.isEmpty() && droneCount < desiredDrones) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        return plans;
    }

    protected int dronesNeeded(GameState gameState) {
        int drones = 16;
        int lairCount = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery);
        if (lairCount > 0) {
            drones += 6;
        }
        if (hatchCount > 2) {
            drones += 6 * (hatchCount - 1);
        }
        return drones;
    }

    private int desiredMutalisks(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (!techProgression.isSpire()) {
            return 0;
        }

        int baseTarget = 11;
        
        // Increase mutalisk target when floating minerals (similar to ThreeHatchLurker hydralisks)
        int availableMinerals = gameState.getResourceCount().availableMinerals();
        if (availableMinerals > 400) {
            int extraMutalisks = availableMinerals / 100;
            baseTarget += Math.min(extraMutalisks, 20);
        }

        return baseTarget;
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    @Override
    public boolean needLair() { return true; }
}
