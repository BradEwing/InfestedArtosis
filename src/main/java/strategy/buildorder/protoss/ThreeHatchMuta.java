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
 *
 * Liquipedia Overview
 * <p>
 * This build opens very similarly to a ZvT 3 Hatch Mutalisk build. It looks to get Scourge and/or Mutalisks out very quickly to control 
 * Protoss' early midgame Tech, allowing Zerg the freedom to power Drones through the midgame. It can sometimes be used as an aggressive strategy 
 * by catching the Protoss main off guard while it has very few Cannons and minimal air defense.
 * <a href="https://github.com/Cmccrave/McRave/blob/699ecf1b0f8581f318b467c74744027f3eb9b852/Source/McRave/Builds/Zerg/ZvP/ZvP.cpp#L245">McRave 3HatchMuta</a>
 * <a href="https://liquipedia.net/starcraft/3_Hatch_Spire_(vs._Protoss)">Liquipedia</a>
 *
 */
public class ThreeHatchMuta extends ProtossBase {

    // TODO: Move to TechProgression or GameState
    private boolean plannedFirstMacroHatch = false;
    private boolean plannedSecondMacroHatch = false;
    private boolean plannedThirdMacroHatch = false;
    private boolean plannedHydraliskDen = false;
    private boolean plannedEvolutionChamber = false;

    public ThreeHatchMuta() {
        super("3HatchMuta");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        Time time = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        boolean cannonRushed = strategyTracker.isDetectedStrategy("CannonRush");
        boolean twoGateRushed = strategyTracker.isDetectedStrategy("2Gate");
        boolean rushed = cannonRushed || twoGateRushed;
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int supply = gameState.getSupply();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int macroHatchCount = baseData.numMacroHatcheries();
        int totalHatcheries = baseCount + macroHatchCount;
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount         = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int spireCount        = gameState.ourUnitCount(UnitType.Zerg_Spire);
        int hydraCount        = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int livingDroneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int enemyCorsairCount = gameState.enemyUnitCount(UnitType.Protoss_Corsair);
        int enemyObserverCount = gameState.enemyUnitCount(UnitType.Protoss_Observer);

        // Gas timing
        boolean gasBlocked = cannonRushed && time.lessThanOrEqual(new Time(4, 0));
        boolean firstGas = !gasBlocked && gameState.canPlanExtractor() && (time.greaterThan(new Time(2, 32)) || supply > 40) && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && (spireCount > 0 || droneCount >= 20);

        // Base timing
        boolean delayThird = rushed && time.lessThanOrEqual(new Time(6,0));
        boolean wantNatural  = plannedAndCurrentHatcheries < 2 && supply >= 24 && !delayThird && !gameState.isCannonRushed();
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && droneCount > 13 && techProgression.isSpawningPool() && !gameState.isCannonRushed();
        boolean wantBaseAdvantage = behindOnBases(gameState);
        boolean floatingMinerals = gameState.isFloatingMinerals();

        // Macro hatchery timing
        boolean cannonRushMacroHatch = gameState.isCannonRushed() && baseCount == 1
                && gameState.getResourceCount().availableMinerals() >= 300;
        boolean wantFirstMacroHatch = cannonRushMacroHatch || wantFirstMacroHatchery(gameState);
        boolean wantSecondMacroHatch = !gameState.isCannonRushed() && wantSecondMacroHatchery(gameState);
        boolean wantThirdMacroHatch = !gameState.isCannonRushed() && wantThirdMacroHatchery(gameState);

        // Lair timing
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && time.greaterThan(new Time(2, 30)) && baseCount >= 2;

        // Spire timing
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1 && livingDroneCount >= 16;

        // Tech building timing
        boolean wantHydraliskDen = wantHydraliskDen(gameState);
        boolean wantEvolutionChamber = wantEvolutionChamber(gameState);

        // Upgrade timing
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;
        boolean wantCarapaceUpgrade = wantCarapaceUpgrade(gameState);
        boolean wantOverlordSpeed = needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed();

        // Plan buildings

        // Defensive Structures
        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        // Bases
        if (wantNatural || wantThird || wantBaseAdvantage || floatingMinerals) {
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

        // Tech Buildings
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

        if (wantCarapaceUpgrade) {
            Plan carapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Carapace);
            plans.add(carapacePlan);
        }

        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
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

        final int desiredMutalisks = desiredMutalisks(gameState);
        if (techProgression.isSpire() && mutaCount < desiredMutalisks) {
            for (int i = 0; i < desiredMutalisks - mutaCount; i++) {
                Plan mutaliskPlan = this.planUnit(gameState, UnitType.Zerg_Mutalisk);
                plans.add(mutaliskPlan);
            }
            return plans;
        }

        final int desiredHydralisks = desiredHydralisks(gameState);
        if (techProgression.isHydraliskDen() && hydraCount < desiredHydralisks) {
            for (int i = 0; i < desiredHydralisks - hydraCount; i++) {
                Plan hydraliskPlan = this.planUnit(gameState, UnitType.Zerg_Hydralisk);
                plans.add(hydraliskPlan);
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

        int droneTarget = totalHatcheries * 8;
        droneTarget = Math.min(droneTarget, 65);
        if (droneCount < droneTarget) {
            for (int i = 0; i < droneTarget - droneCount; i++) {
                Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Drone);
                plans.add(zerglingPlan);
            }
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

        int mutaCount = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        Time gameTime = gameState.getGameTime();

        return mutaCount > 1 || gameTime.greaterThan(new Time(10, 0));
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

        int mutaCount = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        Time gameTime = gameState.getGameTime();

        return mutaCount >= 7 || gameTime.greaterThan(new Time(11, 0));
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

        int mutaCount = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        Time gameTime = gameState.getGameTime();

        return mutaCount >= 8 || gameTime.greaterThan(new Time(12, 0));
    }

    // Tech building planning methods
    private boolean wantHydraliskDen(GameState gameState) {
        if (plannedHydraliskDen) {
            return false;
        }

        if (!plannedFirstMacroHatch) {
            return false;
        }

        TechProgression techProgression = gameState.getTechProgression();
        return techProgression.canPlanHydraliskDen();
    }

    private boolean wantEvolutionChamber(GameState gameState) {
        if (plannedEvolutionChamber) {
            return false;
        }

        if (!plannedHydraliskDen) {
            return false;
        }

        TechProgression techProgression = gameState.getTechProgression();
        return techProgression.canPlanEvolutionChamber();
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
    private int desiredMutalisks(GameState gameState) {
        return 9;
    }

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
        return true; 
    }
}