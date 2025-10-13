package strategy.buildorder.protoss;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import info.tracking.StrategyTracker;
import macro.plan.Plan;
import util.Time;

import java.util.ArrayList;
import java.util.List;

/**
 * 3HatchHydralisk
 *
 * Opens with 3 hatcheries and transitions into Hydralisk production
 * instead of Mutalisks. Focuses on ground army composition.
 */
public class ThreeHatchHydra extends ProtossBase {

    private boolean plannedFirstMacroHatch = false;
    private boolean plannedSecondMacroHatch = false;
    private boolean plannedThirdMacroHatch = false;
    private boolean plannedHydraliskDen = false;
    private boolean plannedEvolutionChamber = false;

    public ThreeHatchHydra() {
        super("3HatchHydra");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        Time time = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        boolean cannonRushed = strategyTracker.isDetectedStrategy("CannonRush");
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int supply = gameState.getSupply();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int macroHatchCount = baseData.numMacroHatcheries();
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery);
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;

        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int droneCount = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        // Gas timing
        boolean gasBlocked = cannonRushed && time.lessThanOrEqual(new Time(4, 0));
        boolean firstGas = !gasBlocked && gameState.canPlanExtractor() && (time.greaterThan(new Time(2, 32)) || supply > 40) && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && (techProgression.isHydraliskDen() || droneCount >= 20);

        // Base timing
        boolean wantNatural = plannedAndCurrentHatcheries < 2 && supply >= 24;
        boolean wantThird = plannedAndCurrentHatcheries < 3 && supply >= 40 && techProgression.isSpawningPool();
        boolean wantBaseAdvantage = behindOnBases(gameState);

        // Macro hatchery timing
        boolean wantFirstMacroHatch = wantFirstMacroHatchery(gameState);
        boolean wantSecondMacroHatch = wantSecondMacroHatchery(gameState);
        boolean wantThirdMacroHatch = wantThirdMacroHatchery(gameState);

        // Tech building timing
        boolean wantHydraliskDen = wantHydraliskDen(gameState);
        boolean wantEvolutionChamber = wantEvolutionChamber(gameState);

        // Upgrade timing
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && zerglingCount > 12;
        boolean wantMuscularAugments = techProgression.canPlanMuscularAugments();
        boolean wantGroovedSpines = techProgression.canPlanGroovedSpines();
        boolean wantRangedUpgrades = wantRangedUpgrade(gameState);
        boolean wantCarapaceUpgrade = wantCarapaceUpgrade(gameState);
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

        // Macro Hatcheries
        if (wantFirstMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plannedFirstMacroHatch = true;
                plans.add(macroHatchPlan);
                return plans;
            }
        }

        if (wantSecondMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plannedSecondMacroHatch = true;
                plans.add(macroHatchPlan);
                return plans;
            }
        }

        if (wantThirdMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plannedThirdMacroHatch = true;
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


        if (wantHydraliskDen) {
            Plan hydraliskDenPlan = planHydraliskDen(gameState);
            if (hydraliskDenPlan != null) {
                plannedHydraliskDen = true;
                plans.add(hydraliskDenPlan);
                return plans;
            }
        }

        if (wantEvolutionChamber) {
            Plan evolutionChamberPlan = planEvolutionChamber(gameState);
            if (evolutionChamberPlan != null) {
                plannedEvolutionChamber = true;
                plans.add(evolutionChamberPlan);
                return plans;
            }
        }

        // Plan Upgrades
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

        // Plan Overlord Speed
        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        // Plan Units

        if (droneCount < 13) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
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

        int droneTarget = hatchCount * 7;
        if (time.lessThanOrEqual(new Time(5, 0))) {
            droneTarget = Math.min(droneTarget, 19);
        }
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

    // Macro hatchery planning methods
    private boolean wantFirstMacroHatchery(GameState gameState) {
        if (plannedFirstMacroHatch) {
            return false;
        }

        int macroHatchCount = gameState.getBaseData().numMacroHatcheries();
        if (macroHatchCount >= 1) {
            return false;
        }

        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        Time gameTime = gameState.getGameTime();

        return hydraCount > 18 || gameTime.greaterThan(new Time(10, 0));
    }

    private boolean wantSecondMacroHatchery(GameState gameState) {
        if (plannedSecondMacroHatch) {
            return false;
        }

        if (plannedFirstMacroHatch && gameState.getGameTime().lessThanOrEqual(new Time(8, 0))) {
            return false;
        }

        int macroHatchCount = gameState.getBaseData().numMacroHatcheries();
        if (macroHatchCount >= 2) {
            return false;
        }

        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        Time gameTime = gameState.getGameTime();

        return hydraCount >= 25 || gameTime.greaterThan(new Time(11, 0));
    }

    private boolean wantThirdMacroHatchery(GameState gameState) {
        if (plannedThirdMacroHatch) {
            return false;
        }

        if (plannedSecondMacroHatch && gameState.getGameTime().lessThanOrEqual(new Time(9, 0))) {
            return false;
        }

        int macroHatchCount = gameState.getBaseData().numMacroHatcheries();
        if (macroHatchCount >= 3) {
            return false;
        }

        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        Time gameTime = gameState.getGameTime();

        return hydraCount >= 35 || gameTime.greaterThan(new Time(12, 0));
    }

    // Tech building planning methods
    private boolean wantHydraliskDen(GameState gameState) {
        if (plannedHydraliskDen || gameState.ourUnitCount(UnitType.Zerg_Extractor) == 0) {
            return false;
        }

        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;

        TechProgression techProgression = gameState.getTechProgression();

        return techProgression.canPlanHydraliskDen() && plannedAndCurrentHatcheries >= 3;
    }

    private boolean wantEvolutionChamber(GameState gameState) {
        if (plannedEvolutionChamber) {
            return false;
        }

        TechProgression techProgression = gameState.getTechProgression();
        final boolean haveDen = techProgression.isHydraliskDen();
        if (!haveDen) {
            return false;
        }

        final int hydras = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);

        return techProgression.canPlanEvolutionChamber() && hydras > 6;
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

    // Unit production methods
    private int desiredHydralisks(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (!techProgression.isHydraliskDen()) {
            return 0;
        }

        return 25;
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
    public boolean needLair() {
        return false;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        final boolean den = gameState.ourUnitCount(UnitType.Zerg_Hydralisk_Den) > 0;
        final int hydras = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        if (den && hydras < 11) {
            return 0;
        }

        return super.zerglingsNeeded(gameState);
    }
}