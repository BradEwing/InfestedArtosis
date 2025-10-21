package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.TechType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import util.Time;

import java.util.ArrayList;
import java.util.List;

public class ThreeHatchLurker extends TerranBase {

    public ThreeHatchLurker() {
        super("3HatchLurker");
    }
    
    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int macroHatchCount = baseData.numMacroHatcheries();
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery);
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int lurkerCount = gameState.ourUnitCount(UnitType.Zerg_Lurker);
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && techProgression.isLair() && extractorCount < 2;

        boolean wantNatural = plannedAndCurrentHatcheries < 2 && droneCount >= 12;

        boolean wantFirstMacroHatch = wantFirstMacroHatchery(gameState);

        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;

        boolean wantHydraliskDen = wantHydraliskDen(gameState);
        boolean wantEvolutionChamber = wantEvolutionChamber(gameState);

        boolean wantLurkerAspect = wantLurkerAspect(gameState);
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && lairCount > 0 && (zerglingCount >= 12 && lurkerCount > 2);
        boolean wantMuscularAugments = techProgression.canPlanMuscularAugments() && hydraCount > 3 && lurkerCount > 0;
        boolean wantGroovedSpines = techProgression.canPlanGroovedSpines() && hydraCount > 6;
        boolean wantRangedUpgrades = wantRangedUpgrade(gameState);
        boolean wantCarapaceUpgrade = wantCarapaceUpgrade(gameState);
        boolean wantOverlordSpeed = needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed();

        // Check for floating resources (follows OneHatchSpire pattern)
        boolean floatingMinerals = gameState.getGameTime().greaterThan(new Time(5, 0)) &&
                gameState.getResourceCount().availableMinerals() > ((plannedHatcheries + 1) * 350);
        boolean wantExpansion = behindOnBases(gameState) || floatingMinerals;

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        if (wantNatural || wantExpansion) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (wantFirstMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plans.add(macroHatchPlan);
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

        if (wantHydraliskDen) {
            Plan hydraliskDenPlan = planHydraliskDen(gameState);
            if (hydraliskDenPlan != null) {
                plans.add(hydraliskDenPlan);
                return plans;
            }
        }

        if (wantEvolutionChamber) {
            Plan evolutionChamberPlan = planEvolutionChamber(gameState);
            if (evolutionChamberPlan != null) {
                plans.add(evolutionChamberPlan);
                return plans;
            }
        }

        if (wantLurkerAspect) {
            Plan lurkerAspectPlan = this.planTech(gameState, TechType.Lurker_Aspect);
            plans.add(lurkerAspectPlan);
            return plans;
        }

        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        boolean plannedMuscularAugmentsThisFrame = false;
        if (wantMuscularAugments) {
            Plan muscularAugmentsPlan = this.planUpgrade(gameState, UpgradeType.Muscular_Augments);
            plans.add(muscularAugmentsPlan);
            plannedMuscularAugmentsThisFrame = true;
        }

        if (wantGroovedSpines && !plannedMuscularAugmentsThisFrame) {
            Plan groovedSpinesPlan = this.planUpgrade(gameState, UpgradeType.Grooved_Spines);
            plans.add(groovedSpinesPlan);
        }

        boolean plannedRangedUpgradesThisFrame = false;
        if (wantRangedUpgrades) {
            Plan rangedPlan = this.planUpgrade(gameState, UpgradeType.Zerg_Missile_Attacks);
            plans.add(rangedPlan);
            plannedRangedUpgradesThisFrame = true;
        }

        if (wantCarapaceUpgrade && !plannedRangedUpgradesThisFrame) {
            Plan carapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Carapace);
            plans.add(carapacePlan);
        }

        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        if (droneCount < 13) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        final int desiredLurkers = desiredLurkers(gameState);
        if (techProgression.isLurker() && lurkerCount < desiredLurkers && hydraCount > 0) {
            Plan lurkerPlan = this.planUnit(gameState, UnitType.Zerg_Lurker);
            plans.add(lurkerPlan);
            return plans;
        }

        final int desiredHydralisks = desiredHydralisks(gameState);
        if (techProgression.isHydraliskDen() && hydraCount < desiredHydralisks) {
            Plan hydraliskPlan = this.planUnit(gameState, UnitType.Zerg_Hydralisk);
            plans.add(hydraliskPlan);
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

        int droneTarget = dronesNeeded(gameState);
        if (macroHatchCount > 0 && droneCount < droneTarget) {
            for (int i = 0; i < droneTarget - droneCount; i++) {
                Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
                plans.add(dronePlan);
            }
            return plans;
        }

        if (plans.isEmpty() && gameState.canPlanDrone() && droneCount < droneTarget) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        return plans;
    }

    private boolean wantFirstMacroHatchery(GameState gameState) {
        int macroHatchCount = gameState.getBaseData().numMacroHatcheries();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int baseCount = gameState.getBaseData().currentBaseCount();
        int lairCount = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int lurkerCount = gameState.ourUnitCount(UnitType.Zerg_Lurker);
        
        if (macroHatchCount >= 1 || (plannedHatcheries + baseCount) >= 3) {
            return false;
        }

        int droneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);

        // Third hatch should wait until at least 2 lurkers are out
        if ((plannedHatcheries + baseCount) >= 2 && lurkerCount < 2) {
            return false;
        }

        return droneCount >= 17 && lairCount > 0;
    }

    private boolean wantHydraliskDen(GameState gameState) {
        if (gameState.ourUnitCount(UnitType.Zerg_Extractor) == 0) {
            return false;
        }

        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;

        TechProgression techProgression = gameState.getTechProgression();

        return techProgression.canPlanHydraliskDen() && plannedAndCurrentHatcheries >= 2;
    }

    private boolean wantLurkerAspect(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        
        if (!techProgression.canPlanLurker()) {
            return false;
        }

        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        
        return hydraCount >= 3;
    }

    private boolean wantEvolutionChamber(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        final boolean haveDen = techProgression.isHydraliskDen();
        final int droneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        if (!haveDen) {
            return false;
        }

        final int lurkers = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker);

        return techProgression.canPlanEvolutionChamber() && lurkers > 3 && droneCount > 18;
    }

    private boolean wantRangedUpgrade(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (techProgression.getEvolutionChambers() < 1) {
            return false;
        }

        return techProgression.canPlanRangedUpgrades() &&
                techProgression.getRangedUpgrades() < 1;
    }

    private boolean wantCarapaceUpgrade(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (techProgression.getEvolutionChambers() < 1) {
            return false;
        }

        return techProgression.canPlanCarapaceUpgrades() &&
                techProgression.getCarapaceUpgrades() < 1;
    }

    private int desiredHydralisks(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (!techProgression.isHydraliskDen()) {
            return 0;
        }

        int baseTarget = 3;
        int lurkerCount = gameState.ourUnitCount(UnitType.Zerg_Lurker);
        if (lurkerCount > 0) {
            baseTarget += 3;
        }
        
        if (isMechComposition(gameState)) {
            baseTarget += 6;
        }
    
        // Increase hydra target when floating minerals (similar to OneHatchSpire mutalisks)
        int availableMinerals = gameState.getResourceCount().availableMinerals();
        if (availableMinerals < 400) {
            return baseTarget;
        }
        int extraHydras = availableMinerals / 75;
        baseTarget += Math.min(extraHydras, 40); 
    
        return baseTarget;
    }

    private int desiredLurkers(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (!techProgression.isLurker()) {
            return 0;
        }

        int baseTarget = 8;

        // Increase lurker target when floating minerals (similar to hydralisks)
        int availableGas = gameState.getResourceCount().availableGas();
        if (availableGas > 400) {
            int extraLurkers = availableGas / 100;
            baseTarget += Math.min(extraLurkers, 30);
        }

        return baseTarget;
    }
    
    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    @Override
    public boolean needLair() {
        return true;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        final boolean den = gameState.ourUnitCount(UnitType.Zerg_Hydralisk_Den) > 0;
        final boolean hasLurkerTech = gameState.getTechProgression().isLurker();
        final int hydras = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        
        if (den && hydras < 3) {
            return 0;
        }

        return super.zerglingsNeeded(gameState);
    }

    protected int dronesNeeded(GameState gameState) {
        int drones = 12;
        
        int lurkerCount = gameState.ourUnitCount(UnitType.Zerg_Lurker);
        int lairCount = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        if (lairCount > 0) {
            drones += 9;
        }
        if (lurkerCount > 2) {
            drones += 6;
        }
        if (hatchCount > 2) {
            drones += 6 * (hatchCount - 2);
        }
        return drones;
    }
}
