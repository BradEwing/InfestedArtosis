package strategy.buildorder;

import bwapi.Player;
import bwapi.Race;
import bwapi.TechType;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import info.UnitTypeCount;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.TechPlan;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import strategy.strategies.UnitWeights;
import util.Time;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a transitory class that captures old production planning logic from the ProductionManager.
 * It will be supplanted with the broader implementation of BuildOrder.
 */
public class Legacy extends BuildOrder {
    public Legacy() {
        super("Legacy");
        this.activatedAt = new Time(0);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List <Plan> plans = new ArrayList<>();
        Player self = gameState.getGame().self();
        Boolean isAllIn = gameState.isAllIn();

        plans.addAll(planBuildings(gameState, isAllIn));
        plans.addAll(planUpgrades(gameState,isAllIn));
        plans.addAll(planUnits(gameState, self, isAllIn));

        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    private List<Plan> planBuildings(GameState gameState, Boolean isAllIn) {
        final int currentFrame = gameState.getGameTime().getFrames();
        List <Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();

        if (gameState.canPlanHatchery()) {
            gameState.addPlannedHatchery(1);
            final int numHatcheries = gameState.getBaseData().numHatcheries();
            if ((numHatcheries % 2) != gameState.getMacroHatchMod()) {
                Plan basePlan = planNewBase(gameState);
                if (basePlan != null) {
                    plans.add(basePlan);
                }
            } else {
                Plan plan = new BuildingPlan(UnitType.Zerg_Hatchery, 2, true);
                plans.add(plan);
            }
        }

        if (gameState.canPlanExtractor()) {
            Plan plan = new BuildingPlan(UnitType.Zerg_Extractor, currentFrame, true);
            Unit geyser = baseData.reserveExtractor();
            plan.setBuildPosition(geyser.getTilePosition());
            plans.add(plan);
        }

        // Build at 10 workers if not part of initial build order
        if (techProgression.canPlanPool()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Spawning_Pool, currentFrame, true));
            techProgression.setPlannedSpawningPool(true);
        }

        if (isAllIn) {
            return plans;
        }

//        if (gameState.canPlanSunkenColony()) {
//            TilePosition tp = baseData.reserveSunkenColony();
//            tp = gameState.getGame().getBuildLocation(UnitType.Zerg_Creep_Colony, tp, 128, true);
//            Plan creepColonyPlan = new BuildingPlan(UnitType.Zerg_Creep_Colony, currentFrame-2, true);
//            Plan sunkenColonyPlan = new BuildingPlan(UnitType.Zerg_Sunken_Colony, currentFrame-1, true);
//            creepColonyPlan.setBuildPosition(tp);
//            sunkenColonyPlan.setBuildPosition(tp);
//            plans.add(creepColonyPlan);
//            plans.add(sunkenColonyPlan);
//        }

        UnitWeights unitWeights = gameState.getUnitWeights();

        if (gameState.canPlanHydraliskDen()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Hydralisk_Den, currentFrame, true));
            techProgression.setPlannedDen(true);
        }

        if (gameState.canPlanEvolutionChamber()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Evolution_Chamber, currentFrame,true));
            final int currentEvolutionChambers = techProgression.evolutionChambers();
            techProgression.setPlannedEvolutionChambers(currentEvolutionChambers+1);
        }

        if (gameState.canPlanLair()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Lair, currentFrame, false));
            techProgression.setPlannedLair(true);
        }

        if (techProgression.canPlanSpire() && unitWeights.hasUnit(UnitType.Zerg_Mutalisk)) {
            plans.add(new BuildingPlan(UnitType.Zerg_Spire, currentFrame, true));
            techProgression.setPlannedSpire(true);
        }

        if (gameState.canPlanQueensNest()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Queens_Nest, currentFrame, true));
            techProgression.setPlannedQueensNest(true);
        }

        if (gameState.canPlanHive()) {
            plans.add(new BuildingPlan(UnitType.Zerg_Hive, currentFrame, true));
            techProgression.setPlannedHive(true);
        }

        return plans;
    }

    /**
     * Plan to take an upgrade.
     *
     * Does not plan if there is no gas; all upgrades require gas.
     *
     * TODO: Track when an upgrade completes
     *
     * NOTE: Potential for reinforcement learning to search when to take an upgrade against an opponent.
     * @param isAllIn
     */
    private List<Plan> planUpgrades(GameState gameState, Boolean isAllIn) {
        final int currentFrame = gameState.getGameTime().getFrames();
        List <Plan> plans = new ArrayList<>();
        BaseData baseData = gameState.getBaseData();

        if (baseData.numExtractor() == 0 || isAllIn) {
            return plans;
        }

        TechProgression techProgression = gameState.getTechProgression();
        UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();

        /** Ling Upgrades **/
        final int numZerglings = unitTypeCount.get(UnitType.Zerg_Zergling);
        if (techProgression.canPlanMetabolicBoost() && numZerglings > 8) {
            plans.add(new UpgradePlan(UpgradeType.Metabolic_Boost, currentFrame, false));
            techProgression.setPlannedMetabolicBoost(true);
        }

        /** Hydra Upgrades */
        final int numHydralisks = unitTypeCount.get(UnitType.Zerg_Hydralisk);
        if (techProgression.canPlanMuscularAugments() && numHydralisks > 4) {
            plans.add(new UpgradePlan(UpgradeType.Muscular_Augments, currentFrame, false));
            techProgression.setPlannedMuscularAugments(true);
        }
        if (techProgression.canPlanGroovedSpines() && numHydralisks > 10) {
            plans.add(new UpgradePlan(UpgradeType.Grooved_Spines, currentFrame, false));
            techProgression.setPlannedGroovedSpines(true);
        }
        final UnitWeights unitWeights = gameState.getUnitWeights();
        if (techProgression.canPlanLurker() && unitWeights.hasUnit(UnitType.Zerg_Lurker)) {
            plans.add(new TechPlan(TechType.Lurker_Aspect, currentFrame, true));
            techProgression.setPlannedLurker(true);
        }


        /** Evolution Chamber Upgrades **/
        // Carapace
        final int evoBuffer = techProgression.evolutionChamberBuffer();
        if (techProgression.canPlanCarapaceUpgrades() && unitTypeCount.groundCount() > 8) {
            plans.add(new UpgradePlan(UpgradeType.Zerg_Carapace, currentFrame+evoBuffer, false));
            techProgression.setPlannedCarapaceUpgrades(true);
        }

        // Ranged Attack
        if (techProgression.canPlanRangedUpgrades() && unitTypeCount.rangedCount() > 12) {
            plans.add(new UpgradePlan(UpgradeType.Zerg_Missile_Attacks, currentFrame+evoBuffer, false));
            techProgression.setPlannedRangedUpgrades(true);
        }

        // Melee Attack
        if (techProgression.canPlanMeleeUpgrades() && unitTypeCount.meleeCount() > 18) {
            plans.add(new UpgradePlan(UpgradeType.Zerg_Melee_Attacks, currentFrame+evoBuffer, false));
            techProgression.setPlannedMeleeUpgrades(true);
        }

        /** Spire Upgrades **/
        // TODO: Reactively weigh attack vs defense from game state
        // For now, prefer attack
        if (techProgression.canPlanFlyerAttack() && unitTypeCount.airCount() > 8) {
            plans.add(new UpgradePlan(UpgradeType.Zerg_Flyer_Attacks, currentFrame, false));
            techProgression.setPlannedFlyerAttack(true);
        }
        if (techProgression.canPlanFlyerDefense() && unitTypeCount.airCount() > 8) {
            final int flyerAttackTime = UpgradeType.Zerg_Flyer_Attacks.upgradeTime();
            plans.add(new UpgradePlan(UpgradeType.Zerg_Flyer_Carapace, currentFrame+1+flyerAttackTime, false));
            techProgression.setPlannedFlyerDefense(true);
        }

        return plans;
    }

    // TODO: Droning vs Combat Units
    private List<Plan> planUnits(GameState gameState, Player self, Boolean isAllIn) {
        final int currentFrame = gameState.getGameTime().getFrames();
        List <Plan> plans = new ArrayList<>();
        if (self.supplyUsed() >= 400) {
            return plans;
        }
        // Plan workers
        // This should be related to num bases + aval min patches and geysers, limited by army and potentially higher level strat info
        // For now, set them to be 1/3 of total supply
        // Limit the number of drones in queue, or they will crowd out production!
        if (!isAllIn && gameState.canPlanDrone()) {
            gameState.addPlannedWorker(1);
            plans.add(createUnitPlan(gameState, UnitType.Zerg_Drone, currentFrame, false));
        }

        UnitWeights unitWeights = gameState.getUnitWeights();

        // Plan army
        // TODO: Determine a better way to pick next unit
        UnitType unitToBuild = getBestUnitToBuild(gameState, unitWeights.getRandom());
        if (unitToBuild == UnitType.Unknown) {
            return plans;
        }
        plans.add(createUnitPlan(gameState, unitToBuild, currentFrame, false));
        return plans;
    }

    private UnitType getBestUnitToBuild(GameState gameState, UnitType initialCandidate) {
        UnitWeights unitWeights = gameState.getUnitWeights();
        UnitTypeCount unitCount = gameState.getUnitTypeCount();
        UnitType nextUnit = initialCandidate;

        final int numHydralisks = unitCount.get(UnitType.Zerg_Hydralisk);
        final int numLurkers =  unitCount.get(UnitType.Zerg_Lurker);
        final int targetLurkers = numHydralisks / 2;
        if (unitWeights.isEnabled(UnitType.Zerg_Lurker) && numLurkers < targetLurkers) {
            return UnitType.Zerg_Lurker;
        }
        return nextUnit;
    }

    private Plan createUnitPlan(GameState gameState, UnitType unitType, int priority, boolean isBlocking) {
        UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();
        unitTypeCount.planUnit(unitType);
        return new UnitPlan(unitType, priority, isBlocking);
    }
}
