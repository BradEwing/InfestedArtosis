package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.ResourceCount;
import info.TechProgression;
import info.tracking.StrategyTracker;
import macro.Reactions;
import macro.plan.Plan;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;
import strategy.buildorder.ZerglingTargets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The hydralisk-focused ZvT build: Hydralisks and Zerglings out before the third base, which is taken
 * once {@value #HYDRALISKS_BEFORE_THIRD_BASE} Hydralisks stand, on a Drone target of
 * {@value #DRONES_BEFORE_HYDRALISKS}. It researches Muscular Augments, Grooved Spines, Missile Attacks
 * and Carapace, and hands over to {@link LurkerDefilerUltra} once
 * {@value LurkerDefilerUltraTransition#HYDRALISK_TRIGGER} Hydralisks are produced, Muscular Augments
 * and Grooved Spines are researched, and the economy gate in {@link LurkerDefilerUltraTransition}
 * is met.
 *
 * <p>It never plans Lurker Aspect or a Lurker morph, so every Hydralisk stays in the army. Defences
 * and the Zergling target come from {@link TerranBase}.
 *
 * <p>Mech is known when the enemy army reads as mech now, or when
 * {@link StrategyTracker#isTerranMechKnown} says it was detected this game or persists across recent
 * games. Known mech raises the Hydralisk target and moves the Hydralisk Den upgrades and the
 * Evolution Chamber upgrades earlier. With no mech evidence the build follows the same sequence at
 * the smaller {@value #BASE_HYDRALISKS} Hydralisk target and the later upgrade thresholds.
 */
public class TwoHatchHydraTerran extends TerranBase {

    public static final String NAME = "2HatchHydraZvT";

    /** Hydralisks wanted once the Den stands and no mech has been seen. */
    static final int BASE_HYDRALISKS = 12;

    /** Hydralisks wanted once the Den stands and mech is known. */
    static final int MECH_HYDRALISKS = 24;

    /** Cap on the Hydralisks added from floating minerals. */
    static final int FLOAT_HYDRALISK_CAP = 40;

    /** Living Hydralisks before the Den upgrades are planned when mech is known. */
    static final int MECH_DEN_UPGRADE_HYDRALISKS = 2;

    /** Living Hydralisks before the Den upgrades are planned with no mech evidence. */
    static final int DEN_UPGRADE_HYDRALISKS = 4;

    /** Living Hydralisks before the Evolution Chamber upgrades are planned when mech is known. */
    static final int MECH_EVOLUTION_UPGRADE_HYDRALISKS = 6;

    /** Living Hydralisks before the Evolution Chamber upgrades are planned with no mech evidence. */
    static final int EVOLUTION_UPGRADE_HYDRALISKS = 10;

    /** Hydralisks owned and planned before Zerglings take larva again. */
    static final int HYDRALISKS_BEFORE_ZERGLINGS = 6;

    /** Living Hydralisks before the Den upgrades move ahead of the Hydralisk stream. */
    static final int HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY = 6;

    /** Living Hydralisks before Missile Attacks and Carapace move ahead of the Hydralisk stream. */
    static final int HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY = 12;

    private static final int UPGRADE_EVOLUTION_CHAMBERS = 2;

    private static final int EVOLUTION_CHAMBER_DRONES = 18;

    private static final int METABOLIC_BOOST_ZERGLINGS = 12;

    /** Hydralisks owned before the third base is asked for. */
    static final int HYDRALISKS_BEFORE_THIRD_BASE = 6;

    /** Hydralisks owned before Drones take larva from the Hydralisk stream. */
    static final int HYDRALISKS_BEFORE_DRONES = 6;

    /** Drones the build takes larva for ahead of the Hydralisk stream once the first Hydralisks stand. */
    static final int DRONES_BEFORE_HYDRALISKS = 18;

    private static final int DRONES_BASE = 18;

    private static final int DRONES_WITH_LAIR = 3;

    private static final int DRONES_PER_EXTRA_HATCHERY = 6;

    public TwoHatchHydraTerran() {
        super(NAME);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        ResourceCount resourceCount = gameState.getResourceCount();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int plannedAndCurrentHatcheries = gameState.getPlannedHatcheries() + baseCount;
        int lairCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int committedDens = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Hydralisk_Den);
        int hydraCount = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int livingHydras = gameState.ourLivingUnitCount(UnitType.Zerg_Hydralisk);
        int droneCount = gameState.numEconomyDrones();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        boolean mech = isMechKnown(gameState);

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && committedDens > 0 && extractorCount < 2;
        boolean extraGas = gameState.canPlanExtractor() && baseCount > 2 && extractorCount < 3
                && resourceCount.availableMinerals() > 300;

        boolean wantNatural = plannedAndCurrentHatcheries < 2 && droneCount >= 12;
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;
        boolean wantHydraliskDen = wantHydraliskDen(gameState);

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && lairCount > 0
                && gameState.ourLivingUnitCount(UnitType.Zerg_Zergling) >= METABOLIC_BOOST_ZERGLINGS
                && livingHydras >= HYDRALISKS_BEFORE_ZERGLINGS;
        boolean wantDenUpgrades = shouldPlanDenUpgrades(livingHydras, mech);
        boolean wantMuscularAugments = techProgression.canPlanMuscularAugments() && wantDenUpgrades;
        boolean wantGroovedSpines = techProgression.canPlanGroovedSpines() && wantDenUpgrades;
        boolean wantEvolutionUpgrades = shouldPlanEvolutionUpgrades(livingHydras, mech);
        boolean wantRangedUpgrades = techProgression.canPlanRangedUpgrades() && wantEvolutionUpgrades;
        boolean wantCarapaceUpgrade = techProgression.canPlanCarapaceUpgrades() && wantEvolutionUpgrades;
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(
                needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantMuscularAugments, wantGroovedSpines, wantRangedUpgrades, wantCarapaceUpgrade);

        int basesHeldOrReserved = baseData.currentAndReservedCount();
        boolean wantExpansion = wantsExpansion(behindOnBases(gameState), gameState.isFloatingMinerals(),
                gameState.totalProduced(UnitType.Zerg_Hydralisk), basesHeldOrReserved)
                || wantsThirdBase(plannedAndCurrentHatcheries, hydraCount);

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        if (wantNatural || wantExpansion) {
            Plan expansionPlan = this.planNewBase(gameState,
                    LurkerDefilerUltraTransition.prefersGasBase(basesHeldOrReserved));
            if (expansionPlan != null) {
                plans.add(expansionPlan);
            }
        }

        if (firstGas || secondGas || extraGas) {
            plans.add(this.planExtractor(gameState));
        }

        if (techProgression.canPlanPool() && droneCount > 10) {
            plans.add(this.planSpawningPool(gameState));
            return plans;
        }

        if (wantLair) {
            plans.add(this.planLair(gameState));
            return plans;
        }

        if (wantHydraliskDen) {
            Plan hydraliskDenPlan = planHydraliskDen(gameState);
            if (hydraliskDenPlan != null) {
                plans.add(hydraliskDenPlan);
                return plans;
            }
        }

        if (wantEvolutionChamber(gameState, techProgression, livingHydras, mech)) {
            Plan evolutionChamberPlan = planEvolutionChamber(gameState);
            if (evolutionChamberPlan != null) {
                plans.add(evolutionChamberPlan);
                return plans;
            }
        }

        boolean plannedMuscularAugmentsThisFrame = false;
        if (wantMuscularAugments) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Muscular_Augments));
            plannedMuscularAugmentsThisFrame = true;
        }

        if (wantGroovedSpines && !plannedMuscularAugmentsThisFrame) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Grooved_Spines));
        }

        boolean plannedRangedUpgradesThisFrame = false;
        if (wantRangedUpgrades) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Missile_Attacks));
            plannedRangedUpgradesThisFrame = true;
        }

        if (wantCarapaceUpgrade && !plannedRangedUpgradesThisFrame) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Carapace));
        }

        if (wantMetabolicBoost) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Metabolic_Boost));
        }

        if (wantOverlordSpeed) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace));
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (shouldDroneBeforeZerglings(droneCount, zerglingCount, desiredZerglings)) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        final int desiredHydralisks = desiredHydralisks(techProgression.isHydraliskDen(), mech,
                resourceCount.availableMinerals());
        final boolean canPlanHydralisk = canPlanAdvancedUnit(gameState, UnitType.Zerg_Hydralisk);
        final int droneTarget = dronesNeeded(gameState);
        UnitType next = nextArmyUnit(techProgression.isHydraliskDen(), hydraCount, desiredHydralisks,
                canPlanHydralisk, zerglingCount, desiredZerglings, droneCount, droneTarget);

        if (next == UnitType.Zerg_Hydralisk) {
            List<Plan> hydraliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Hydralisk);
            if (!hydraliskPlans.isEmpty()) {
                plans.addAll(hydraliskPlans);
                return plans;
            }
            next = nextArmyUnit(techProgression.isHydraliskDen(), hydraCount, desiredHydralisks, false,
                    zerglingCount, desiredZerglings, droneCount, droneTarget);
        }

        if (next == UnitType.Zerg_Zergling) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (next == UnitType.Zerg_Drone && gameState.canPlanDrone()) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        Plan surplusPlan = this.planMineralSurplusUnit(gameState);
        if (surplusPlan != null) {
            plans.add(surplusPlan);
        }

        return plans;
    }

    /**
     * Whether mech is known: the enemy army reads as mech now, or the strategy tracker has it
     * detected this game or persisting across recent games.
     */
    private boolean isMechKnown(GameState gameState) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        return isMechComposition(gameState) || strategyTracker != null && strategyTracker.isTerranMechKnown();
    }

    private boolean wantHydraliskDen(GameState gameState) {
        if (gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Extractor) == 0) {
            return false;
        }
        int plannedAndCurrentHatcheries = gameState.getPlannedHatcheries()
                + gameState.getBaseData().currentBaseCount();
        return gameState.getTechProgression().canPlanHydraliskDen() && plannedAndCurrentHatcheries >= 2;
    }

    private boolean wantEvolutionChamber(GameState gameState, TechProgression techProgression, int livingHydras,
                                         boolean mech) {
        return techProgression.isHydraliskDen()
                && shouldPlanUpgradeEvolutionChamber(techProgression, UPGRADE_EVOLUTION_CHAMBERS)
                && shouldPlanEvolutionUpgrades(livingHydras, mech)
                && gameState.numGatherers() > EVOLUTION_CHAMBER_DRONES;
    }

    /**
     * The Hydralisk target. It is zero until the Den stands, then {@value #MECH_HYDRALISKS} when mech
     * is known and {@value #BASE_HYDRALISKS} otherwise, plus one per 75 floating minerals once the
     * bank holds {@link LarvaBoundMacroHatchery#FLOAT_MINERALS} or more, capped at
     * {@value #FLOAT_HYDRALISK_CAP}.
     *
     * @param denStands whether the Hydralisk Den is finished
     * @param mech whether mech is known
     * @param availableMinerals minerals not already reserved
     * @return Hydralisks to own and queue
     */
    static int desiredHydralisks(boolean denStands, boolean mech, int availableMinerals) {
        if (!denStands) {
            return 0;
        }
        int target = mech ? MECH_HYDRALISKS : BASE_HYDRALISKS;
        if (availableMinerals < LarvaBoundMacroHatchery.FLOAT_MINERALS) {
            return target;
        }
        return target + Math.min(availableMinerals / 75, FLOAT_HYDRALISK_CAP);
    }

    /**
     * Whether Muscular Augments and Grooved Spines may be planned: once
     * {@value #MECH_DEN_UPGRADE_HYDRALISKS} Hydralisks are fielded against known mech, else
     * {@value #DEN_UPGRADE_HYDRALISKS}. Fielded rather than planned, so the research does not start
     * against a bank the Hydralisks still need.
     */
    static boolean shouldPlanDenUpgrades(int livingHydras, boolean mech) {
        return livingHydras >= (mech ? MECH_DEN_UPGRADE_HYDRALISKS : DEN_UPGRADE_HYDRALISKS);
    }

    /**
     * Whether Missile Attacks, Carapace and the Evolution Chamber they need may be planned: once
     * {@value #MECH_EVOLUTION_UPGRADE_HYDRALISKS} Hydralisks are fielded against known mech, else
     * {@value #EVOLUTION_UPGRADE_HYDRALISKS}.
     */
    static boolean shouldPlanEvolutionUpgrades(int livingHydras, boolean mech) {
        return livingHydras >= (mech ? MECH_EVOLUTION_UPGRADE_HYDRALISKS : EVOLUTION_UPGRADE_HYDRALISKS);
    }

    /**
     * Whether the build takes its third base: with fewer than three hatcheries held or planned, once
     * {@value #HYDRALISKS_BEFORE_THIRD_BASE} Hydralisks are owned, so the army is out before the
     * Hatchery goes down.
     */
    static boolean wantsThirdBase(int plannedAndCurrentHatcheries, int hydralisksOwned) {
        return plannedAndCurrentHatcheries >= 2
                && plannedAndCurrentHatcheries < LurkerDefilerUltraTransition.ECONOMY_BASES
                && hydralisksOwned >= HYDRALISKS_BEFORE_THIRD_BASE;
    }

    /**
     * Whether the next larva morphs a Drone ahead of the Hydralisk stream: once the Den stands and
     * {@value #HYDRALISKS_BEFORE_DRONES} Hydralisks are owned, while fewer than
     * {@value #DRONES_BEFORE_HYDRALISKS} Drones gather.
     *
     * @param denStands whether the Hydralisk Den is finished
     * @param hydralisksOwned Hydralisks owned and queued
     * @param droneCount gathering plus queued drones
     * @return true when a Drone outranks the next Hydralisk
     */
    static boolean shouldDroneBeforeHydralisks(boolean denStands, int hydralisksOwned, int droneCount) {
        return denStands && hydralisksOwned >= HYDRALISKS_BEFORE_DRONES && droneCount < DRONES_BEFORE_HYDRALISKS;
    }

    /**
     * The unit type the army step of the build morphs next, or null when it morphs none. The build
     * only ever picks from Hydralisk, Zergling and Drone, so no Lurker morph is reachable. Drones
     * outrank the Hydralisk stream once the first {@value #HYDRALISKS_BEFORE_DRONES} Hydralisks stand
     * and until {@value #DRONES_BEFORE_HYDRALISKS} Drones gather.
     *
     * @param denStands whether the Hydralisk Den is finished
     * @param hydralisksOwned Hydralisks owned and queued
     * @param desiredHydralisks the Hydralisk target
     * @param canPlanHydralisk whether a Hydralisk plan is currently allowed
     * @param zerglingsOwned Zerglings owned and queued
     * @param desiredZerglings the Zergling target
     * @param droneCount gathering plus queued drones
     * @param droneTarget the Drone target
     * @return the unit type to morph next
     */
    static UnitType nextArmyUnit(boolean denStands, int hydralisksOwned, int desiredHydralisks,
                                 boolean canPlanHydralisk, int zerglingsOwned, int desiredZerglings,
                                 int droneCount, int droneTarget) {
        if (shouldDroneBeforeHydralisks(denStands, hydralisksOwned, droneCount)) {
            return UnitType.Zerg_Drone;
        }
        if (canPlanHydralisk && hydralisksOwned < desiredHydralisks) {
            return UnitType.Zerg_Hydralisk;
        }
        if (zerglingsOwned < desiredZerglings) {
            return UnitType.Zerg_Zergling;
        }
        return droneCount < droneTarget ? UnitType.Zerg_Drone : null;
    }

    /**
     * Whether the build asks for an expansion beyond the third base it takes on the economy: when
     * behind on bases, when floating minerals, or for the handover base once the trigger's Hydralisk count is produced.
     */
    static boolean wantsExpansion(boolean behindOnBases, boolean floatingMinerals, int hydralisksProduced,
                                  int basesHeldOrReserved) {
        return behindOnBases || floatingMinerals
                || hydralisksProduced >= LurkerDefilerUltraTransition.HYDRALISK_TRIGGER
                && basesHeldOrReserved < LurkerDefilerUltraTransition.ECONOMY_BASES;
    }

    /**
     * The drone target: the main and natural's {@value #DRONES_BASE}, {@value #DRONES_WITH_LAIR} more
     * with a Lair, and {@value #DRONES_PER_EXTRA_HATCHERY} per hatchery past the second.
     */
    static int droneTarget(int lairCount, int hatcheryCount) {
        int drones = DRONES_BASE;
        if (lairCount > 0) {
            drones += DRONES_WITH_LAIR;
        }
        if (hatcheryCount > 2) {
            drones += DRONES_PER_EXTRA_HATCHERY * (hatcheryCount - 2);
        }
        return drones;
    }

    /**
     * Muscular Augments and Grooved Spines move ahead of the Hydralisk stream once
     * {@value #HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY} Hydralisks are alive, and Missile Attacks and
     * Carapace once {@value #HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY} are.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Muscular_Augments:
            case Grooved_Spines:
                return new ArmyUpgradeTrigger(HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY, UnitType.Zerg_Hydralisk);
            case Zerg_Missile_Attacks:
            case Zerg_Carapace:
                return new ArmyUpgradeTrigger(HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY, UnitType.Zerg_Hydralisk);
            default:
                return null;
        }
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        final boolean gasReachable = ZerglingTargets.gasUnitReachable(gameState.getGeyserWorkers(),
                gameState.getResourceCount().availableGas(), UnitType.Zerg_Hydralisk.gasPrice());

        return ZerglingTargets.gasUnitFocus(super.zerglingsNeeded(gameState),
                gameState.getTechProgression().isHydraliskDen(), gameState.ourUnitCount(UnitType.Zerg_Hydralisk),
                HYDRALISKS_BEFORE_ZERGLINGS, gasReachable);
    }

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return Collections.singleton(UnitType.Zerg_Hydralisk);
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return dronesNeeded(gameState);
    }

    protected int dronesNeeded(GameState gameState) {
        return droneTarget(gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair),
                gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hatchery, UnitType.Zerg_Lair,
                        UnitType.Zerg_Hive));
    }

    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return LarvaBoundMacroHatchery.isHydraliskTechReady(techProgression);
    }

    @Override
    public boolean playsRace(Race race) {
        return race == Race.Terran;
    }

    @Override
    public boolean needLair() {
        return true;
    }

    /**
     * Hands over to {@link LurkerDefilerUltra} once the build has produced its Hydralisks and
     * researched their upgrades, and the economy gate in {@link LurkerDefilerUltraTransition} is met.
     */
    @Override
    public boolean shouldTransition(GameState gameState) {
        return LurkerDefilerUltraTransition.shouldEnter(gameState, getName(),
                LurkerDefilerUltraTransition.threeHatchHydraTrigger(
                        gameState.totalProduced(UnitType.Zerg_Hydralisk),
                        gameState.getTechProgression().isMuscularAugments(),
                        gameState.getTechProgression().isGroovedSpines()));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return LurkerDefilerUltraTransition.candidates();
    }
}
