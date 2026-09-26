package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.TechType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.ResourceCount;
import info.TechProgression;
import macro.Reactions;
import macro.plan.Plan;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.LarvaBoundMacroHatchery;
import strategy.buildorder.ZerglingTargets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThreeHatchLurker extends TerranBase {

    static final int HYDRALISKS_BEFORE_ZERGLINGS = 3;

    private static final int UPGRADE_EVOLUTION_CHAMBERS = 2;

    static final int HYDRALISKS_AND_LURKERS_BEFORE_DEN_UPGRADE_PRIORITY = 8;

    static final int HYDRALISKS_AND_LURKERS_BEFORE_EVOLUTION_UPGRADE_PRIORITY = 12;

    public ThreeHatchLurker() {
        super("3HatchLurker");
    }
    
    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        ResourceCount resourceCount = gameState.getResourceCount();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int macroHatchCount = baseData.numMacroHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int committedLairs = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Lair);
        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int droneCount = gameState.numEconomyDrones();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && committedLairs > 0 && extractorCount < 2;
        boolean extraGas = gameState.canPlanExtractor() && baseCount > 2 && resourceCount.availableMinerals() > 400;

        boolean wantNatural = plannedAndCurrentHatcheries < 2 && droneCount >= 12;

        boolean wantFirstMacroHatch = wantFirstMacroHatchery(gameState);

        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;

        boolean wantHydraliskDen = wantHydraliskDen(gameState);

        boolean wantLurkerAspect = wantLurkerAspect(gameState);
        int livingLurkerCount = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker);
        int livingHydraCount = gameState.ourLivingUnitCount(UnitType.Zerg_Hydralisk);
        int livingZerglingCount = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && lairCount > 0
                && shouldPlanMetabolicBoost(livingZerglingCount, livingLurkerCount);
        boolean wantMuscularAugments = techProgression.canPlanMuscularAugments()
                && shouldPlanMuscularAugments(livingHydraCount, livingLurkerCount);
        boolean wantGroovedSpines = techProgression.canPlanGroovedSpines() && shouldPlanGroovedSpines(livingHydraCount);
        boolean wantRangedUpgrades = techProgression.canPlanRangedUpgrades();
        boolean wantCarapaceUpgrade = techProgression.canPlanCarapaceUpgrades();
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantMuscularAugments, wantGroovedSpines, wantRangedUpgrades, wantCarapaceUpgrade);

        // Check for floating resources (follows OneHatchSpire pattern)
        boolean floatingMinerals = gameState.isFloatingMinerals();
        boolean wantExpansion = behindOnBases(gameState) || floatingMinerals;

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        Plan expansionPlan = null;
        if (wantNatural || wantExpansion) {
            expansionPlan = this.planNewBase(gameState);
            if (expansionPlan != null) {
                plans.add(expansionPlan);
            }
        }

        if (expansionPlan == null && wantFirstMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plans.add(macroHatchPlan);
                return plans;
            }
        }

        if (firstGas || secondGas || extraGas) {
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

        if (wantEvolutionChamber(gameState)) {
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

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (shouldDroneBeforeZerglings(droneCount, zerglingCount, desiredZerglings)) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        final int outstandingLurkers = gameState.outstandingUnitPlanCount(UnitType.Zerg_Lurker);
        final int lurkerPipeline = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker) + outstandingLurkers;
        final int livingHydralisks = gameState.ourLivingUnitCount(UnitType.Zerg_Hydralisk);
        final int desiredLurkers = desiredLurkers(gameState, livingHydralisks);
        if (techProgression.isLurker()
                && lurkerPipeline < desiredLurkers
                && livingHydralisks > outstandingLurkers) {
            plans.addAll(this.planAdvancedUnit(gameState, UnitType.Zerg_Lurker));
        }

        final int desiredHydralisks = desiredHydralisks(gameState);
        if (techProgression.isHydraliskDen() && hydraCount < desiredHydralisks && canPlanAdvancedUnit(gameState, UnitType.Zerg_Hydralisk)) {
            List<Plan> hydraliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Hydralisk);
            if (!hydraliskPlans.isEmpty()) {
                plans.addAll(hydraliskPlans);
                return plans;
            }
        }

        if (zerglingCount < desiredZerglings) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        int droneTarget = dronesNeeded(gameState);
        if (macroHatchCount > 0 && droneCount < droneTarget) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        if (plans.isEmpty() && gameState.canPlanDrone() && droneCount < droneTarget) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        Plan surplusPlan = this.planMineralSurplusUnit(gameState);
        if (surplusPlan != null) {
            plans.add(surplusPlan);
        }

        return plans;
    }

    private boolean wantFirstMacroHatchery(GameState gameState) {
        int macroHatchCount = gameState.getBaseData().numMacroHatcheries();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        int baseCount = gameState.getBaseData().currentBaseCount();
        int lairCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int livingLurkerCount = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker);
        
        if (macroHatchCount >= 1 || (plannedHatcheries + baseCount) >= 3) {
            return false;
        }

        int droneCount = gameState.numGatherers();

        // Third hatch should wait until at least 2 lurkers are out
        if ((plannedHatcheries + baseCount) >= 2 && !hasFieldedLurkersForThirdHatch(livingLurkerCount)) {
            return false;
        }

        return droneCount >= 17 && lairCount > 0;
    }

    static boolean hasFieldedLurkersForThirdHatch(int livingLurkerCount) {
        return livingLurkerCount >= 2;
    }

    /**
     * Metabolic Boost waits until the zerglings it upgrades and the lurkers it complements are
     * fielded, not merely queued. A queued Zergling plan adds two to the planned count, so a gate
     * read off planned units clears on eggs and plans with no army to upgrade.
     *
     * @param livingZerglingCount zerglings already on the field
     * @param livingLurkerCount lurkers already on the field
     * @return whether the upgrade may be planned
     */
    static boolean shouldPlanMetabolicBoost(int livingZerglingCount, int livingLurkerCount) {
        return livingZerglingCount >= 12 && livingLurkerCount > 2;
    }

    /**
     * Muscular Augments waits until the hydralisks it upgrades and the lurkers they support are
     * fielded. A planned Hydralisk has not been given larva or gas yet, and the upgrade waits on
     * neither larva nor a morph, so counting plans starts it against the bank those Hydralisks
     * still need.
     *
     * @param livingHydraCount hydralisks already on the field
     * @param livingLurkerCount lurkers already on the field
     * @return whether the upgrade may be planned
     */
    static boolean shouldPlanMuscularAugments(int livingHydraCount, int livingLurkerCount) {
        return livingHydraCount > 3 && livingLurkerCount >= 2;
    }

    /**
     * Grooved Spines waits until the hydralisks it upgrades are fielded, for the same reason as
     * {@link #shouldPlanMuscularAugments}.
     *
     * @param livingHydraCount hydralisks already on the field
     * @return whether the upgrade may be planned
     */
    static boolean shouldPlanGroovedSpines(int livingHydraCount) {
        return livingHydraCount > 6;
    }

    /**
     * Muscular Augments and Grooved Spines move ahead of the Hydralisk and Lurker stream once
     * {@value #HYDRALISKS_AND_LURKERS_BEFORE_DEN_UPGRADE_PRIORITY} Hydralisks and Lurkers are alive,
     * and Missile Attacks and Carapace once
     * {@value #HYDRALISKS_AND_LURKERS_BEFORE_EVOLUTION_UPGRADE_PRIORITY} are.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Muscular_Augments:
            case Grooved_Spines:
                return new ArmyUpgradeTrigger(HYDRALISKS_AND_LURKERS_BEFORE_DEN_UPGRADE_PRIORITY,
                        UnitType.Zerg_Hydralisk, UnitType.Zerg_Lurker);
            case Zerg_Missile_Attacks:
            case Zerg_Carapace:
                return new ArmyUpgradeTrigger(HYDRALISKS_AND_LURKERS_BEFORE_EVOLUTION_UPGRADE_PRIORITY,
                        UnitType.Zerg_Hydralisk, UnitType.Zerg_Lurker);
            default:
                return null;
        }
    }

    private boolean wantHydraliskDen(GameState gameState) {
        if (gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Extractor) == 0) {
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

    /**
     * Whether the upgrade path should plan an Evolution Chamber. Missile Attacks and Carapace run
     * in parallel, one chamber each, and a chamber the Spore branch queued counts towards the two.
     */
    private boolean wantEvolutionChamber(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        final boolean haveDen = techProgression.isHydraliskDen();
        final int droneCount = gameState.numGatherers();
        if (!haveDen) {
            return false;
        }

        final int lurkers = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker);

        return shouldPlanUpgradeEvolutionChamber(techProgression, UPGRADE_EVOLUTION_CHAMBERS)
                && lurkers > 3 && droneCount > 18;
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

    /**
     * Lurkers to aim for, never more than the hydralisks that could morph into them plus the ones
     * already on the field. Asking past the producer pool queues plans nothing can execute.
     *
     * @param gameState current game state
     * @param livingHydralisks hydralisks alive now, the only units that can morph
     * @return the Lurker target for this frame
     */
    private int desiredLurkers(GameState gameState, int livingHydralisks) {
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

        return reachableLurkerTarget(baseTarget, gameState.ourLivingUnitCount(UnitType.Zerg_Lurker), livingHydralisks);
    }

    /**
     * Trims a Lurker target to what the units on the field could actually reach.
     *
     * @param baseTarget the strategic target before any producer limit
     * @param livingLurkers Lurkers already on the field
     * @param livingHydralisks hydralisks that could still morph
     * @return the target the build order should ask for
     */
    static int reachableLurkerTarget(int baseTarget, int livingLurkers, int livingHydralisks) {
        return Math.min(baseTarget, livingLurkers + livingHydralisks);
    }
    
    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return LarvaBoundMacroHatchery.isLurkerTechReady(techProgression);
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
        final boolean den = gameState.getTechProgression().isHydraliskDen();
        final int hydras = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        final boolean gasReachable = ZerglingTargets.gasUnitReachable(gameState.getGeyserWorkers(),
                gameState.getResourceCount().availableGas(), UnitType.Zerg_Hydralisk.gasPrice());

        return ZerglingTargets.gasUnitFocus(super.zerglingsNeeded(gameState), den, hydras,
                HYDRALISKS_BEFORE_ZERGLINGS, gasReachable);
    }

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return new HashSet<>(Arrays.asList(UnitType.Zerg_Lurker, UnitType.Zerg_Hydralisk));
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return dronesNeeded(gameState);
    }

    protected int dronesNeeded(GameState gameState) {
        int drones = 12;
        
        int lurkerCount = gameState.ourUnitCount(UnitType.Zerg_Lurker);
        int lairCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int hatchCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
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
