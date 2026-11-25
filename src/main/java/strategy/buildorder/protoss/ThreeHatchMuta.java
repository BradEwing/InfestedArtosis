package strategy.buildorder.protoss;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import info.tracking.StrategyTracker;
import info.tracking.protoss.ProxyGate;
import macro.Reactions;
import macro.plan.Plan;
import macro.plan.PlanType;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.GasBoundHiveTech;
import strategy.buildorder.LarvaBoundMacroHatchery;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

    static final int UPGRADE_EVOLUTION_CHAMBERS = 2;

    /** Living Hydralisks before Muscular Augments, Grooved Spines or the second Evolution Chamber is planned. */
    static final int HYDRALISKS_BEFORE_DEN_UPGRADES = ThreeHatchHydra.HYDRALISKS_BEFORE_DEN_UPGRADE_PRIORITY;

    /** Living Hydralisks before Missile Attacks or the Hive branch is planned. */
    static final int HYDRALISKS_BEFORE_MISSILE_ATTACKS = ThreeHatchHydra.HYDRALISKS_BEFORE_EVOLUTION_UPGRADE_PRIORITY;

    /** Living Zerglings before Melee Attacks is planned. Tuning constant. */
    static final int ZERGLINGS_BEFORE_MELEE_ATTACKS = 12;

    /** Living Mutalisks before a flyer upgrade level is planned. */
    static final int MUTALISKS_BEFORE_FLYER_UPGRADE = 7;

    /** The Hydralisk target while the bank is not floating. */
    static final int BASE_HYDRALISKS = 25;

    /**
     * Unreserved gas the Hydralisk target needs before it floats while no Hive stands: the Hive
     * branch bar plus one Hydralisk, so a floated Hydralisk never takes the bank below the bar the
     * Queen's Nest waits on.
     */
    static final int HYDRALISK_FLOAT_GAS = GasBoundHiveTech.BRANCH_GAS + UnitType.Zerg_Hydralisk.gasPrice();

    // TODO: Move to TechProgression or GameState
    private boolean plannedFirstMacroHatch = false;
    private boolean plannedSecondMacroHatch = false;
    private boolean plannedThirdMacroHatch = false;
    private boolean plannedEarlyRushMacroHatch = false;

    public ThreeHatchMuta() {
        super("3HatchMuta");
    }

    private int droneTarget(GameState gameState) {
        BaseData baseData = gameState.getBaseData();
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        int droneTarget = (baseData.currentBaseCount() + baseData.numMacroHatcheries()) * 8;
        if (strategyTracker.isDetectedStrategy("FFE") || strategyTracker.isDetectedStrategy("NexusFirst")) {
            droneTarget += 8;
        }
        return Math.min(droneTarget, 65);
    }

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return Collections.singleton(UnitType.Zerg_Mutalisk);
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return droneTarget(gameState);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        Time time = gameState.getGameTime();
        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        boolean cannonRushed = strategyTracker.isDetectedStrategy("CannonRush");
        boolean twoGateRushed = strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME);
        boolean rushed = cannonRushed || twoGateRushed;
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int supply = gameState.getSupply();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount         = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        int spireCount        = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spire);
        int committedSpires   = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spire);
        int hydraCount        = gameState.ourUnitCount(UnitType.Zerg_Hydralisk);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int livingDroneCount = gameState.ourLivingUnitCount(UnitType.Zerg_Drone);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int livingHydralisks  = gameState.ourLivingUnitCount(UnitType.Zerg_Hydralisk);
        int livingZerglings   = gameState.ourLivingUnitCount(UnitType.Zerg_Zergling);
        int livingMutalisks   = gameState.ourLivingUnitCount(UnitType.Zerg_Mutalisk);
        int enemyCorsairCount = gameState.enemyUnitCount(UnitType.Protoss_Corsair);
        int enemyObserverCount = gameState.enemyUnitCount(UnitType.Protoss_Observer);

        // Gas timing
        boolean gasBlocked = cannonRushed && time.lessThanOrEqual(new Time(4, 0));
        boolean firstGas = !gasBlocked && gameState.canPlanExtractor() && (time.greaterThan(new Time(2, 32)) || supply > 40) && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && (committedSpires > 0 || droneCount >= 20);

        // Base timing
        boolean delayThird = rushed && time.lessThanOrEqual(new Time(6,0));
        boolean wantNatural  = plannedAndCurrentHatcheries < 2 && supply >= 24 && !delayThird && !gameState.isCannonRushed();
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && droneCount > 13 && techProgression.isSpawningPool() && !gameState.isCannonRushed();
        boolean wantBaseAdvantage = behindOnBases(gameState);
        boolean floatingMinerals = gameState.isFloatingMinerals();

        // Macro hatchery timing
        boolean cannonRushMacroHatch = gameState.isCannonRushed() && baseCount == 1
                && gameState.getResourceCount().availableMinerals() >= 300;
        boolean earlyRushMacroHatch = gameState.isEarlyRushMacroHatch() && baseCount == 1
                && !plannedEarlyRushMacroHatch
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
        boolean wantQueensNest = wantGasBoundHiveTech(gameState, UnitType.Zerg_Queens_Nest,
                gameState.canPlanQueensNest() && hiveBranchReady(techProgression, livingHydralisks));
        boolean wantHive = shouldPlanHive(gameState.canPlanHive(), techProgression.isPlannedOverlordSpeed());
        boolean gasHeldForResearch = isGasHeldForResearch(gameState.getProductionQueue(),
                gameState.getResourceCount().availableGas());

        // Upgrade timing
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;
        boolean wantMuscularAugments = shouldPlanMuscularAugments(techProgression, livingHydralisks);
        boolean wantGroovedSpines = shouldPlanGroovedSpines(techProgression, livingHydralisks);
        List<UpgradeType> chamberUpgrades = chamberUpgradesToPlan(techProgression, livingHydralisks, livingZerglings);
        UpgradeType flyerUpgrade = flyerUpgradeToPlan(techProgression, livingMutalisks);
        boolean wantAdrenalGlands = techProgression.canPlanAdrenalGlands();
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed()
                        && !wantHive && !techProgression.isPlannedHive(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantMuscularAugments, wantGroovedSpines, !chamberUpgrades.isEmpty(), flyerUpgrade != null,
                wantAdrenalGlands);

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
        boolean wantExpansion = !gameState.isEarlyRushMacroHatch()
                && (wantNatural || wantThird || wantBaseAdvantage || floatingMinerals);
        Plan expansionPlan = null;
        if (wantExpansion) {
            expansionPlan = this.planNewBase(gameState);
            if (expansionPlan != null) {
                plans.add(expansionPlan);
            }
        }

        // Macro Hatcheries
        if (expansionPlan == null && earlyRushMacroHatch) {
            Plan mainHatchPlan = planMacroHatcheryAt(gameState, gameState.getBaseData().getMainBase());
            if (mainHatchPlan != null) {
                plannedEarlyRushMacroHatch = true;
                plannedFirstMacroHatch = true;
                plans.add(mainHatchPlan);
                return plans;
            }
        }

        if (expansionPlan == null && wantFirstMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plannedFirstMacroHatch = true;
                plans.add(macroHatchPlan);
                return plans;
            }
        }

        if (expansionPlan == null && wantSecondMacroHatch) {
            Plan macroHatchPlan = planMacroHatchery(gameState);
            if (macroHatchPlan != null) {
                plannedSecondMacroHatch = true;
                plans.add(macroHatchPlan);
                return plans;
            }
        }

        if (expansionPlan == null && wantThirdMacroHatch) {
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
                plans.add(hydraliskDenPlan);
                return plans;
            }
        }

        if (wantEvolutionChamber(techProgression, livingHydralisks)) {
            Plan evolutionChamberPlan = planEvolutionChamber(gameState);
            if (evolutionChamberPlan != null) {
                plans.add(evolutionChamberPlan);
                return plans;
            }
        }

        if (wantQueensNest) {
            Plan queensNestPlan = this.planQueensNest(gameState);
            plans.add(queensNestPlan);
            return plans;
        }

        if (wantHive) {
            Plan hivePlan = this.planHive(gameState);
            plans.add(hivePlan);
            return plans;
        }

        // Plan Upgrades
        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        if (wantMuscularAugments) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Muscular_Augments));
        }

        if (wantGroovedSpines) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Grooved_Spines));
        }

        for (UpgradeType chamberUpgrade : chamberUpgrades) {
            plans.add(this.planUpgrade(gameState, chamberUpgrade));
        }

        if (flyerUpgrade != null) {
            plans.add(this.planUpgrade(gameState, flyerUpgrade));
        }

        if (wantAdrenalGlands) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Adrenal_Glands));
        }

        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        // Plan Units
        final int desiredScourge = enemyCorsairCount + enemyObserverCount;
        if (techProgression.isSpire() && scourgeCount < desiredScourge && canPlanAdvancedUnit(gameState, UnitType.Zerg_Scourge)) {
            List<Plan> scourgePlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Scourge);
            if (!scourgePlans.isEmpty()) {
                plans.addAll(scourgePlans);
                return plans;
            }
        }

        final int desiredMutalisks = desiredMutalisks(gameState);
        if (!gasHeldForResearch && techProgression.isSpire() && mutaCount < desiredMutalisks
                && canPlanAdvancedUnit(gameState, UnitType.Zerg_Mutalisk)) {
            List<Plan> mutaliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Mutalisk);
            if (!mutaliskPlans.isEmpty()) {
                plans.addAll(mutaliskPlans);
                return plans;
            }
        }

        final int desiredHydralisks = desiredHydralisks(techProgression.isHydraliskDen(), hydraCount,
                gameState.getResourceCount().availableMinerals(), gameState.getResourceCount().availableGas(),
                techProgression.isHive(), livingDroneCount >= droneTarget(gameState));
        if (!gasHeldForResearch && techProgression.isHydraliskDen() && hydraCount < desiredHydralisks
                && canPlanAdvancedUnit(gameState, UnitType.Zerg_Hydralisk)) {
            List<Plan> hydraliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Hydralisk);
            if (!hydraliskPlans.isEmpty()) {
                plans.addAll(hydraliskPlans);
                return plans;
            }
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (zerglingCount < desiredZerglings) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        if (droneCount < droneTarget(gameState)) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        Plan surplusPlan = this.planMineralSurplusUnit(gameState);
        if (surplusPlan != null) {
            plans.add(surplusPlan);
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
        int macroHatcheries = gameState.getBaseData().numMacroHatcheries()
                + gameState.inFlightHatcheryPlans(true)
                + gameState.hatcheriesUnderConstruction(true);
        return shouldPlanHydraliskDen(gameState.getTechProgression(), plannedFirstMacroHatch, macroHatcheries);
    }

    /**
     * The Hydralisk Den follows the first macro hatchery, whichever step bought it: this build's own
     * macro hatchery branch, or the shared larva-bound macro hatchery step.
     *
     * @param techProgression our tech state
     * @param plannedFirstMacroHatch true once this build has planned its first macro hatchery
     * @param macroHatcheries completed macro hatcheries, plus macro hatcheries under construction and
     *                        macro hatchery plans in flight
     */
    static boolean shouldPlanHydraliskDen(TechProgression techProgression, boolean plannedFirstMacroHatch,
            int macroHatcheries) {
        if (!plannedFirstMacroHatch && macroHatcheries < 1) {
            return false;
        }

        return techProgression.canPlanHydraliskDen();
    }

    /**
     * Whether the upgrade path should plan an Evolution Chamber.
     *
     * <p>The first chamber is taken once a Hydralisk Den is planned or standing and researches
     * Carapace. The second follows once the first stands and
     * {@value #HYDRALISKS_BEFORE_DEN_UPGRADES} Hydralisks are alive, so a second upgrade line can
     * run beside the first. A chamber the Spore branch queued, in this pass or earlier, counts
     * towards the two.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @return true when a chamber should be queued now
     */
    static boolean wantEvolutionChamber(TechProgression techProgression, int livingHydralisks) {
        if (!shouldPlanUpgradeEvolutionChamber(techProgression, UPGRADE_EVOLUTION_CHAMBERS)) {
            return false;
        }

        if (techProgression.evolutionChambers() == 0) {
            return techProgression.isPlannedDen() || techProgression.isHydraliskDen();
        }

        return techProgression.isHydraliskDen() && livingHydralisks >= HYDRALISKS_BEFORE_DEN_UPGRADES;
    }

    /**
     * Whether Muscular Augments should be queued. Until {@value #HYDRALISKS_BEFORE_DEN_UPGRADES}
     * Hydralisks are alive the gas goes to Hydralisks.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @return true when the upgrade should be queued now
     */
    static boolean shouldPlanMuscularAugments(TechProgression techProgression, int livingHydralisks) {
        return livingHydralisks >= HYDRALISKS_BEFORE_DEN_UPGRADES && techProgression.canPlanMuscularAugments();
    }

    /**
     * Whether Grooved Spines should be queued. The Hydralisk Den researches one upgrade at a time,
     * so it follows Muscular Augments rather than waiting in the queue behind it.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @return true when the upgrade should be queued now
     */
    static boolean shouldPlanGroovedSpines(TechProgression techProgression, int livingHydralisks) {
        return livingHydralisks >= HYDRALISKS_BEFORE_DEN_UPGRADES
                && techProgression.isMuscularAugments()
                && techProgression.canPlanGroovedSpines();
    }

    /**
     * Whether the next Missile Attacks level should be queued.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @return true when the level should be queued now
     */
    static boolean shouldPlanMissileAttacks(TechProgression techProgression, int livingHydralisks) {
        return livingHydralisks >= HYDRALISKS_BEFORE_MISSILE_ATTACKS && techProgression.canPlanRangedUpgrades();
    }

    /**
     * Whether the next Melee Attacks level should be queued: Carapace 1 is complete and
     * {@value #ZERGLINGS_BEFORE_MELEE_ATTACKS} Zerglings are alive.
     *
     * @param techProgression the bot's tech state
     * @param livingZerglings Zerglings alive on the map
     * @return true when the level should be queued now
     */
    static boolean shouldPlanMeleeAttacks(TechProgression techProgression, int livingZerglings) {
        return techProgression.getCarapaceUpgrades() >= 1
                && livingZerglings >= ZERGLINGS_BEFORE_MELEE_ATTACKS
                && techProgression.canPlanMeleeUpgrades();
    }

    /**
     * The Evolution Chamber upgrades to queue this pass.
     *
     * <p>Missile Attacks, Carapace and Melee Attacks share the chambers. A level is queued only
     * for a chamber that no queued or researching level has claimed, so each standing chamber
     * researches one line and no level waits in the queue on a busy chamber. When more lines are
     * ready than chambers are free, the line at the lowest level goes first, and on equal levels
     * Missile Attacks, then Carapace, then Melee Attacks.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @param livingZerglings Zerglings alive on the map
     * @return the upgrades to queue, at most one for each free chamber
     */
    static List<UpgradeType> chamberUpgradesToPlan(TechProgression techProgression, int livingHydralisks,
            int livingZerglings) {
        List<UpgradeType> ready = new ArrayList<>();
        if (shouldPlanMissileAttacks(techProgression, livingHydralisks)) {
            ready.add(UpgradeType.Zerg_Missile_Attacks);
        }
        if (techProgression.canPlanCarapaceUpgrades()) {
            ready.add(UpgradeType.Zerg_Carapace);
        }
        if (shouldPlanMeleeAttacks(techProgression, livingZerglings)) {
            ready.add(UpgradeType.Zerg_Melee_Attacks);
        }
        ready.sort(Comparator.comparingInt(upgrade -> chamberUpgradeLevel(techProgression, upgrade)));

        int freeChambers = Math.max(0, techProgression.getEvolutionChambers() - chamberUpgradesInFlight(techProgression));
        return new ArrayList<>(ready.subList(0, Math.min(freeChambers, ready.size())));
    }

    private static int chamberUpgradeLevel(TechProgression techProgression, UpgradeType upgradeType) {
        switch (upgradeType) {
            case Zerg_Missile_Attacks:
                return techProgression.getRangedUpgrades();
            case Zerg_Melee_Attacks:
                return techProgression.getMeleeUpgrades();
            default:
                return techProgression.getCarapaceUpgrades();
        }
    }

    private static int chamberUpgradesInFlight(TechProgression techProgression) {
        int inFlight = 0;
        if (techProgression.isPlannedRangedUpgrades()) {
            inFlight++;
        }
        if (techProgression.isPlannedCarapaceUpgrades()) {
            inFlight++;
        }
        if (techProgression.isPlannedMeleeUpgrades()) {
            inFlight++;
        }
        return inFlight;
    }

    /**
     * The flyer upgrade level to queue this pass, if any.
     *
     * <p>Reads living Mutalisks, so each level waits for the Mutalisks it upgrades. The Spire
     * researches one level at a time, so nothing is queued while either line is queued or
     * researching. The line at the lower level goes next, Flyer Attacks first on equal levels.
     *
     * @param techProgression the bot's tech state
     * @param livingMutalisks Mutalisks alive on the map
     * @return the upgrade to queue, or null
     */
    static UpgradeType flyerUpgradeToPlan(TechProgression techProgression, int livingMutalisks) {
        if (livingMutalisks < MUTALISKS_BEFORE_FLYER_UPGRADE) {
            return null;
        }
        if (techProgression.isPlannedFlyerAttack() || techProgression.isPlannedFlyerDefense()) {
            return null;
        }

        boolean attack = techProgression.canPlanFlyerAttack();
        boolean carapace = techProgression.canPlanFlyerDefense();
        if (attack && (!carapace || techProgression.getFlyerAttack() <= techProgression.getFlyerDefense())) {
            return UpgradeType.Zerg_Flyer_Attacks;
        }
        return carapace ? UpgradeType.Zerg_Flyer_Carapace : null;
    }

    /**
     * Whether the build has fielded the army the Hive branch follows: the Spire and the Hydralisk
     * Den stand and {@value #HYDRALISKS_BEFORE_MISSILE_ATTACKS} Hydralisks are alive. Before that
     * the gas bank is the one the first Mutalisks and Hydralisks are waiting to spend.
     *
     * @param techProgression the bot's tech state
     * @param livingHydralisks Hydralisks alive on the map
     * @return true once the Queen's Nest may be requested
     */
    static boolean hiveBranchReady(TechProgression techProgression, int livingHydralisks) {
        return techProgression.isSpire()
                && techProgression.isHydraliskDen()
                && livingHydralisks >= HYDRALISKS_BEFORE_MISSILE_ATTACKS;
    }

    /**
     * Each army upgrade moves ahead of the advanced unit stream once the units that plan it are
     * alive: the Hydralisk Den upgrades on {@value #HYDRALISKS_BEFORE_DEN_UPGRADES} Hydralisks,
     * Missile Attacks on {@value #HYDRALISKS_BEFORE_MISSILE_ATTACKS}, Carapace on that many
     * Hydralisks and Zerglings together, Melee Attacks and Adrenal Glands on
     * {@value #ZERGLINGS_BEFORE_MELEE_ATTACKS} Zerglings, and the flyer upgrades on
     * {@value #MUTALISKS_BEFORE_FLYER_UPGRADE} Mutalisks.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Muscular_Augments:
            case Grooved_Spines:
                return new ArmyUpgradeTrigger(HYDRALISKS_BEFORE_DEN_UPGRADES, UnitType.Zerg_Hydralisk);
            case Zerg_Missile_Attacks:
                return new ArmyUpgradeTrigger(HYDRALISKS_BEFORE_MISSILE_ATTACKS, UnitType.Zerg_Hydralisk);
            case Zerg_Carapace:
                return new ArmyUpgradeTrigger(HYDRALISKS_BEFORE_MISSILE_ATTACKS, UnitType.Zerg_Hydralisk,
                        UnitType.Zerg_Zergling);
            case Zerg_Melee_Attacks:
            case Adrenal_Glands:
                return new ArmyUpgradeTrigger(ZERGLINGS_BEFORE_MELEE_ATTACKS, UnitType.Zerg_Zergling);
            case Zerg_Flyer_Attacks:
            case Zerg_Flyer_Carapace:
                return new ArmyUpgradeTrigger(MUTALISKS_BEFORE_FLYER_UPGRADE, UnitType.Zerg_Mutalisk);
            default:
                return null;
        }
    }

    // Unit production methods
    private int desiredMutalisks(GameState gameState) {
        return 9;
    }

    /**
     * The Hydralisk target.
     *
     * <p>{@value #BASE_HYDRALISKS} while the bank is ordinary. Once the Drone target is met, the
     * unreserved minerals reach the surplus bar and the unreserved gas reaches the float bar, the
     * target sits one above the Hydralisks already counted, so the surplus buys Hydralisks one
     * plan at a time for as long as all three hold. The float bar is {@link #HYDRALISK_FLOAT_GAS}
     * until a Hive stands, which leaves the Hive branch its gas, and one Hydralisk's gas after.
     *
     * @param hydraliskDen whether the Hydralisk Den has finished
     * @param hydralisks Hydralisks counted toward the target, planned ones included
     * @param availableMinerals minerals mined and not reserved by a queued plan
     * @param availableGas gas mined and not reserved by a queued plan
     * @param hive whether a Hive stands
     * @param droneTargetMet whether the build has the Drones it wants
     * @return the Hydralisk count the build wants
     */
    static int desiredHydralisks(boolean hydraliskDen, int hydralisks, int availableMinerals, int availableGas,
            boolean hive, boolean droneTargetMet) {
        if (!hydraliskDen) {
            return 0;
        }
        int floatGas = hive ? UnitType.Zerg_Hydralisk.gasPrice() : HYDRALISK_FLOAT_GAS;
        if (!droneTargetMet || availableMinerals < MINERAL_SURPLUS || availableGas < floatGas) {
            return BASE_HYDRALISKS;
        }
        return Math.max(BASE_HYDRALISKS, hydralisks + 1);
    }

    /**
     * Whether the Mutalisk and Hydralisk streams are withheld so a queued upgrade can be paid for.
     *
     * <p>An upgrade short of gas reserves nothing, and a Hydralisk or Mutalisk the bank can
     * already pay for is scheduled behind it, so each unit takes the gas the upgrade is saving
     * toward. While an upgrade at or ahead of {@link BuildOrder#ARMY_UPGRADE_PRIORITY} waits in the
     * queue for more gas than is unreserved, the build plans neither unit. The hold ends as soon
     * as the bank covers the upgrade's gas.
     *
     * @param queuedPlans plans waiting in the production queue
     * @param availableGas gas mined and not reserved by a queued plan
     * @return true while the gas units should not be planned
     */
    static boolean isGasHeldForResearch(Iterable<Plan> queuedPlans, int availableGas) {
        for (Plan plan : queuedPlans) {
            if (plan.getType() == PlanType.UPGRADE
                    && plan.getPriority() <= BuildOrder.ARMY_UPGRADE_PRIORITY
                    && plan.gasPrice() > availableGas) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the Hive should be planned. The Lair cannot morph while it researches, so the Hive
     * waits for Pneumatized Carapace to leave the Lair rather than holding its cost against it.
     *
     * @param canPlanHive whether the Queen's Nest stands and no Hive is planned or standing
     * @param overlordSpeedInFlight whether Pneumatized Carapace is queued or researching
     * @return true when the Hive should be queued now
     */
    static boolean shouldPlanHive(boolean canPlanHive, boolean overlordSpeedInFlight) {
        return canPlanHive && !overlordSpeedInFlight;
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
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return LarvaBoundMacroHatchery.isSpireReady(techProgression);
    }

    @Override
    public boolean needLair() { 
        return true; 
    }

    @Override
    public boolean needHive() {
        return true;
    }
}
