package strategy.buildorder.terran;

import bwapi.TechType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import macro.Reactions;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.LarvaBoundMacroHatchery;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The terminal ZvT build: Lurker, Zergling and Hydralisk on four bases, teching straight to Hive
 * for Defilers and then Ultralisks.
 *
 * <p>Entered from 2HatchMuta and 3HatchLurker through {@link LurkerDefilerUltraTransition}, never
 * chosen as an opener, and never transitions out. Every tech step is skipped when the structure
 * or research already stands or is planned, so the build picks up from whatever the build before
 * it finished.
 *
 * <p>The tech path, in the order {@link #nextTechStep} reads it: Hydralisk Den, one Evolution
 * Chamber, Queen's Nest, Lurker Aspect, Hive, then the Defiler Mound the moment the Hive finishes.
 * Consume is researched first and Plague only once Consume is done. Ultralisks follow the first
 * Defiler once {@value #ULTRALISK_GEYSERS} geysers are being mined.
 *
 * @see <a href="https://liquipedia.net/starcraft/3_Hatch_Muta_(vs._Terran)">Liquipedia: 3 Base Hive
 *     Lurker Defiler</a>
 * @see <a href="https://liquipedia.net/starcraft/Defiler">Liquipedia: Defiler</a>
 * @see <a href="https://liquipedia.net/starcraft/Ultralisk">Liquipedia: Ultralisk</a>
 */
public class LurkerDefilerUltra extends TerranBase {

    public static final String NAME = "LurkerDefilerUltra";

    /** Bases the build expands to on its own. */
    static final int BASE_TARGET = 4;

    /** Bases held before a macro Hatchery may be added. */
    static final int MACRO_HATCHERY_MIN_BASES = 4;

    /**
     * Macro Hatcheries the build allows, counting those finished, under construction and planned,
     * including any the build before it made.
     */
    static final int MACRO_HATCHERY_CAP = 2;

    /**
     * The Hive the Queen's Nest fallback is timed against: about 12 minutes, the ticket's
     * acceptance bar.
     */
    static final Time HIVE_DUE = new Time(12, 0);

    /**
     * The game time past which the Queen's Nest is planned on fewer than three bases:
     * {@link #HIVE_DUE} less the Queen's Nest and Hive build times, so a Nest started then can
     * still put the Hive up by {@link #HIVE_DUE}.
     */
    static final Time QUEENS_NEST_DUE = new Time(HIVE_DUE.getFrames()
            - UnitType.Zerg_Queens_Nest.buildTime() - UnitType.Zerg_Hive.buildTime());

    /** Bases that plan the Queen's Nest before {@link #QUEENS_NEST_DUE}: the 3 Base Hive. */
    static final int QUEENS_NEST_BASES = 3;

    /**
     * Geysers with gas left under a completed Extractor before Ultralisks are allowed. Liquipedia:
     * Ultralisks usually come once four geysers are harvested.
     */
    static final int ULTRALISK_GEYSERS = 4;

    /** Defilers morphed before Ultralisks are allowed. */
    static final int DEFILERS_BEFORE_ULTRALISKS = 1;

    /** Defilers the build keeps, the same count CrazyZerg keeps. */
    static final int DESIRED_DEFILERS = 3;

    /**
     * Priority of a Defiler plan: one ahead of the advanced unit band, so a Defiler takes the gas
     * before a Lurker or Ultralisk queued alongside it.
     */
    static final int DEFILER_PRIORITY = UnitPlan.ADVANCED_UNIT_PRIORITY - 1;

    /** Lurkers the build aims for. Liquipedia: about a control group of Lurkers. */
    static final int LURKER_TARGET = 12;

    /** Hydralisks asked for per living enemy flyer, on top of those the Lurkers will morph from. */
    static final int HYDRALISKS_PER_ENEMY_FLYER = 2;

    /** Most Hydralisks the enemy flyers can ask for. */
    static final int MAX_ANTI_AIR_HYDRALISKS = 24;

    /** Ultralisks the build keeps once they are allowed. */
    static final int ULTRALISK_TARGET = 6;

    /** Zergling floor once the Hive stands, the same floor CrazyZerg uses. */
    static final int HIVE_ZERGLINGS = 24;

    /** Zergling floor before the Hive. */
    static final int LAIR_ZERGLINGS = 12;

    /** Ultralisks alive before the Ultralisk upgrades move ahead of the Ultralisk stream. */
    static final int ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY = 3;

    /** The hatchery the build asks for this frame, from {@link #hatcheryStep}. */
    enum HatcheryStep {
        NEW_BASE,
        MACRO_HATCHERY,
        NONE
    }

    /** The next tech structure or research the build plans, in the order {@link #nextTechStep} reads them. */
    enum TechStep {
        DEFILER_MOUND,
        HIVE,
        LAIR,
        HYDRALISK_DEN,
        EVOLUTION_CHAMBER,
        QUEENS_NEST,
        LURKER_ASPECT,
        ULTRALISK_CAVERN,
        NONE
    }

    public LurkerDefilerUltra() {
        super(NAME);
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int droneCount = gameState.numEconomyDrones();
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int committedLairOrHive = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        boolean ultraliskGate = ultraliskGate(gameState.totalProduced(UnitType.Zerg_Defiler), gameState.miningGeysers());
        boolean floatingMinerals = gameState.isFloatingMinerals();

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        Plan hatcheryPlan = planHatchery(gameState, baseCount, floatingMinerals);
        if (hatcheryPlan != null) {
            plans.add(hatcheryPlan);
        }

        if (gameState.canPlanExtractor() && extractorCount < baseCount) {
            plans.add(this.planExtractor(gameState));
        }

        if (techProgression.canPlanPool()) {
            plans.add(this.planSpawningPool(gameState));
            return plans;
        }

        boolean wantLair = gameState.canPlanLair() && committedLairOrHive == 0;
        Plan techPlan = planTechStep(gameState, nextTechStep(techProgression, wantLair, baseCount,
                gameState.getGameTime(), ultraliskGate));
        if (techPlan != null) {
            plans.add(techPlan);
        }

        TechType research = nextDefilerResearch(techProgression);
        if (research != TechType.None) {
            plans.add(this.planTech(gameState, research));
        }

        plans.addAll(planUpgrades(gameState, techProgression));

        List<Plan> defilerPlans = planDefiler(gameState, techProgression);
        plans.addAll(defilerPlans);

        final int outstandingLurkers = gameState.outstandingUnitPlanCount(UnitType.Zerg_Lurker);
        final int livingLurkers = gameState.ourLivingUnitCount(UnitType.Zerg_Lurker);
        final int livingHydralisks = gameState.ourLivingUnitCount(UnitType.Zerg_Hydralisk);
        final int lurkerPipeline = livingLurkers + outstandingLurkers;
        final int lurkerTarget = ThreeHatchLurker.reachableLurkerTarget(LURKER_TARGET, livingLurkers, livingHydralisks);
        if (techProgression.isLurker() && lurkerPipeline < lurkerTarget && livingHydralisks > outstandingLurkers) {
            plans.addAll(this.planAdvancedUnit(gameState, UnitType.Zerg_Lurker));
        }

        if (ultraliskGate && techProgression.isUltraliskCavern()
                && gameState.ourUnitCount(UnitType.Zerg_Ultralisk) < ULTRALISK_TARGET
                && canPlanAdvancedUnit(gameState, UnitType.Zerg_Ultralisk)) {
            plans.addAll(this.planAdvancedUnit(gameState, UnitType.Zerg_Ultralisk));
        }

        int hydraliskTarget = hydraliskTarget(techProgression.isLurker() || techProgression.isPlannedLurker(),
                lurkerPipeline, enemyFlyers(gameState));
        if (techProgression.isHydraliskDen() && gameState.ourUnitCount(UnitType.Zerg_Hydralisk) < hydraliskTarget
                && canPlanAdvancedUnit(gameState, UnitType.Zerg_Hydralisk)) {
            plans.addAll(this.planAdvancedUnit(gameState, UnitType.Zerg_Hydralisk));
        }

        if (droneCount < dronesNeeded(gameState) && gameState.canPlanDrone()) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
        }

        if (zerglingCount < this.zerglingsNeeded(gameState)) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
            return plans;
        }

        Plan surplusPlan = this.planMineralSurplusUnit(gameState);
        if (surplusPlan != null) {
            plans.add(surplusPlan);
        }

        return plans;
    }

    /**
     * The hatchery the build asks for this frame, if any.
     *
     * <p>Below {@value #BASE_TARGET} bases, or when the enemy holds as many bases as we do, a new
     * base. On {@value #MACRO_HATCHERY_MIN_BASES} or more bases a minerals float buys a macro
     * Hatchery while fewer than {@value #MACRO_HATCHERY_CAP} exist, and a new base once they do.
     */
    private Plan planHatchery(GameState gameState, int baseCount, boolean floatingMinerals) {
        switch (hatcheryStep(gameState.getBaseData().currentAndReservedCount(), behindOnBases(gameState),
                floatingMinerals, baseCount, macroHatcheries(gameState))) {
            case MACRO_HATCHERY:
                return this.planMacroHatchery(gameState);
            case NEW_BASE:
                return this.planNewBase(gameState);
            default:
                return null;
        }
    }

    /**
     * The hatchery the build asks for, as {@link #planHatchery} describes.
     *
     * @param basesHeldOrReserved bases we hold or have reserved for a queued hatchery
     * @param behindOnBases whether the enemy holds as many bases as we do
     * @param floatingMinerals whether {@link GameState#isFloatingMinerals()} holds
     * @param baseCount bases with a hatchery of ours
     * @param macroHatcheries macro Hatcheries finished, under construction and planned
     * @return the step to take this frame
     */
    static HatcheryStep hatcheryStep(int basesHeldOrReserved, boolean behindOnBases, boolean floatingMinerals,
                                     int baseCount, int macroHatcheries) {
        boolean wantBase = basesHeldOrReserved < BASE_TARGET || behindOnBases;
        if (!wantBase && floatingMinerals && macroHatcheryAllowed(baseCount, macroHatcheries)) {
            return HatcheryStep.MACRO_HATCHERY;
        }
        if (wantBase || floatingMinerals) {
            return HatcheryStep.NEW_BASE;
        }
        return HatcheryStep.NONE;
    }

    private static int macroHatcheries(GameState gameState) {
        return gameState.getBaseData().numMacroHatcheries()
                + gameState.inFlightHatcheryPlans(true)
                + gameState.hatcheriesUnderConstruction(true);
    }

    /**
     * Whether another macro Hatchery is allowed.
     *
     * @param baseCount bases with a hatchery of ours
     * @param macroHatcheries macro Hatcheries finished, under construction and planned
     * @return true on {@value #MACRO_HATCHERY_MIN_BASES} or more bases while fewer than
     *     {@value #MACRO_HATCHERY_CAP} macro Hatcheries exist
     */
    static boolean macroHatcheryAllowed(int baseCount, int macroHatcheries) {
        return baseCount >= MACRO_HATCHERY_MIN_BASES && macroHatcheries < MACRO_HATCHERY_CAP;
    }

    /**
     * The shared larva-bound macro hatchery step runs under the same cap as the build's own float
     * step, so neither path takes the build past {@value #MACRO_HATCHERY_CAP}.
     */
    @Override
    protected boolean allowsLarvaBoundMacroHatchery(GameState gameState) {
        return macroHatcheryAllowed(gameState.getBaseData().currentBaseCount(), macroHatcheries(gameState));
    }

    /**
     * The next tech step, the first one in this order that can be planned: the Defiler Mound, the
     * Hive, a missing Lair, the Hydralisk Den, the first Evolution Chamber, the Queen's Nest, Lurker
     * Aspect, the second Evolution Chamber once the Hive stands, and the Ultralisk Cavern.
     *
     * <p>The Mound and the Hive lead because neither can be planned until its prerequisite
     * finishes, so each is taken the frame that happens, ahead of anything else still open.
     *
     * @param techProgression the bot's tech state; a planned or standing structure is skipped
     * @param wantLair whether a Lair should be planned: none is planned or standing and the Lair gates allow it
     * @param baseCount bases with a hatchery of ours
     * @param gameTime current game time
     * @param ultraliskGate whether Ultralisks are allowed, from {@link #ultraliskGate}
     * @return the step to plan this frame, or {@link TechStep#NONE}
     */
    static TechStep nextTechStep(TechProgression techProgression, boolean wantLair, int baseCount, Time gameTime,
                                 boolean ultraliskGate) {
        if (techProgression.canPlanDefilerMound()) {
            return TechStep.DEFILER_MOUND;
        }
        if (techProgression.canPlanHive()) {
            return TechStep.HIVE;
        }
        if (wantLair) {
            return TechStep.LAIR;
        }
        if (techProgression.canPlanHydraliskDen()) {
            return TechStep.HYDRALISK_DEN;
        }
        if (shouldPlanUpgradeEvolutionChamber(techProgression, 1)) {
            return TechStep.EVOLUTION_CHAMBER;
        }
        if (techProgression.canPlanQueensNest() && queensNestDue(baseCount, gameTime)) {
            return TechStep.QUEENS_NEST;
        }
        if (techProgression.canPlanLurker()) {
            return TechStep.LURKER_ASPECT;
        }
        if (techProgression.isHive() && shouldPlanUpgradeEvolutionChamber(techProgression, 2)) {
            return TechStep.EVOLUTION_CHAMBER;
        }
        if (ultraliskGate && techProgression.canPlanUltraliskCavern()) {
            return TechStep.ULTRALISK_CAVERN;
        }
        return TechStep.NONE;
    }

    /**
     * Whether the Queen's Nest is due: on {@value #QUEENS_NEST_BASES} bases, or past
     * {@link #QUEENS_NEST_DUE} on fewer.
     */
    static boolean queensNestDue(int baseCount, Time gameTime) {
        return baseCount >= QUEENS_NEST_BASES || gameTime.getFrames() >= QUEENS_NEST_DUE.getFrames();
    }

    private Plan planTechStep(GameState gameState, TechStep step) {
        switch (step) {
            case DEFILER_MOUND:
                return this.planDefilerMound(gameState);
            case HIVE:
                return this.planHive(gameState);
            case LAIR:
                return this.planLair(gameState);
            case HYDRALISK_DEN:
                return this.planHydraliskDen(gameState);
            case EVOLUTION_CHAMBER:
                return this.planEvolutionChamber(gameState);
            case QUEENS_NEST:
                return this.planQueensNest(gameState);
            case LURKER_ASPECT:
                return this.planTech(gameState, TechType.Lurker_Aspect);
            case ULTRALISK_CAVERN:
                return this.planUltraliskCavern(gameState);
            default:
                return null;
        }
    }

    /**
     * The Defiler Mound research to plan: Consume first, and Plague only once Consume is done.
     *
     * @param techProgression the bot's tech state
     * @return Consume, Plague, or {@link TechType#None}
     */
    static TechType nextDefilerResearch(TechProgression techProgression) {
        if (techProgression.canPlanConsume()) {
            return TechType.Consume;
        }
        if (techProgression.isConsume() && techProgression.canPlanPlague()) {
            return TechType.Plague;
        }
        return TechType.None;
    }

    /**
     * Whether Ultralisks, and the Ultralisk Cavern, are allowed.
     *
     * @param defilersMorphed Defilers this game has produced
     * @param miningGeysers our completed Extractors on geysers with gas left
     * @return true once {@value #DEFILERS_BEFORE_ULTRALISKS} Defiler has been morphed and
     *     {@value #ULTRALISK_GEYSERS} geysers are being mined
     */
    static boolean ultraliskGate(int defilersMorphed, int miningGeysers) {
        return defilersMorphed >= DEFILERS_BEFORE_ULTRALISKS && miningGeysers >= ULTRALISK_GEYSERS;
    }

    /**
     * A Defiler plan while fewer than {@value #DESIRED_DEFILERS} are alive or planned, at
     * {@link #DEFILER_PRIORITY}. An open drone round does not withhold it: the build's Defilers
     * come ahead of its economy as well as its army.
     */
    private List<Plan> planDefiler(GameState gameState, TechProgression techProgression) {
        if (!techProgression.isDefilerMound() || gameState.ourUnitCount(UnitType.Zerg_Defiler) >= DESIRED_DEFILERS) {
            return new ArrayList<>();
        }
        List<Plan> plans = planAdvancedUnit(UnitType.Zerg_Defiler, techProgression, gameState.numGatherers(),
                gameState.queuedUnitPlanCount(UnitType.Zerg_Defiler), gameState.getUnitTypeCount());
        for (Plan plan : plans) {
            plan.setPriority(DEFILER_PRIORITY);
        }
        return plans;
    }

    private List<Plan> planUpgrades(GameState gameState, TechProgression techProgression) {
        List<Plan> plans = new ArrayList<>();
        boolean hasHive = techProgression.isHive();
        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost();
        boolean wantCarapace = techProgression.canPlanCarapaceUpgrades();
        boolean wantMelee = techProgression.canPlanMeleeUpgrades() && techProgression.evolutionChambers() >= 2;
        boolean wantAdrenalGlands = hasHive && techProgression.canPlanAdrenalGlands();
        boolean wantChitinousPlating = techProgression.canPlanChitinousPlating();
        boolean wantAnabolicSynthesis = techProgression.canPlanAnabolicSynthesis() && techProgression.isChitinousPlating();
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(
                needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantCarapace, wantMelee, wantChitinousPlating, wantAnabolicSynthesis, wantAdrenalGlands);

        if (wantMetabolicBoost) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Metabolic_Boost));
        }
        if (wantCarapace) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Carapace));
        }
        if (wantMelee) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Melee_Attacks));
        }
        if (wantChitinousPlating) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Chitinous_Plating));
        }
        if (wantAnabolicSynthesis) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Anabolic_Synthesis));
        }
        if (wantAdrenalGlands) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Adrenal_Glands));
        }
        if (wantOverlordSpeed) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace));
        }
        return plans;
    }

    private static int enemyFlyers(GameState gameState) {
        return gameState.enemyUnitCount(UnitType.Terran_Wraith)
                + gameState.enemyUnitCount(UnitType.Terran_Valkyrie)
                + gameState.enemyUnitCount(UnitType.Terran_Battlecruiser)
                + gameState.enemyUnitCount(UnitType.Terran_Dropship)
                + gameState.enemyUnitCount(UnitType.Terran_Science_Vessel);
    }

    /**
     * Hydralisks the build asks for: those the Lurker target still needs to morph from, plus
     * {@value #HYDRALISKS_PER_ENEMY_FLYER} per living enemy flyer up to
     * {@value #MAX_ANTI_AIR_HYDRALISKS}.
     *
     * @param lurkerTech whether Lurker Aspect is researched or planned
     * @param lurkerPipeline Lurkers alive plus those planned or morphing
     * @param enemyFlyers living enemy flyers we have observed
     * @return the Hydralisk target, alive plus planned
     */
    static int hydraliskTarget(boolean lurkerTech, int lurkerPipeline, int enemyFlyers) {
        int forLurkers = lurkerTech ? Math.max(0, LURKER_TARGET - lurkerPipeline) : 0;
        int forAir = Math.min(MAX_ANTI_AIR_HYDRALISKS, enemyFlyers * HYDRALISKS_PER_ENEMY_FLYER);
        return forLurkers + forAir;
    }

    /**
     * Drones the build aims for: every worker our bases and mining geysers can use, from
     * {@link GameState#expectedWorkers}.
     */
    private int dronesNeeded(GameState gameState) {
        return GameState.expectedWorkers(gameState.getOpponentRace(), gameState.getBaseData().currentBaseCount(),
                gameState.miningGeysers());
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        return zerglingTarget(super.zerglingsNeeded(gameState), techProgression.isSpawningPool(),
                techProgression.isHive());
    }

    /**
     * The Zergling target: the matchup's own target, already capped by {@link TerranBase}, raised
     * to {@value #LAIR_ZERGLINGS} before the Hive and {@value #HIVE_ZERGLINGS} after it. Zero while
     * no Spawning Pool is finished, since a Zergling plan made then holds a larva it cannot morph.
     * Minerals past the target go to the mineral surplus step.
     *
     * @param matchupZerglings the target {@link TerranBase} asks for, zero once met
     * @param poolReady whether a Spawning Pool is finished
     * @param hasHive whether the Hive stands
     * @return the Zergling target, alive plus planned
     */
    static int zerglingTarget(int matchupZerglings, boolean poolReady, boolean hasHive) {
        if (!poolReady) {
            return 0;
        }
        int floor = hasHive ? HIVE_ZERGLINGS : LAIR_ZERGLINGS;
        return Math.max(matchupZerglings, floor);
    }

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return new HashSet<>(Arrays.asList(UnitType.Zerg_Lurker, UnitType.Zerg_Hydralisk, UnitType.Zerg_Ultralisk));
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return dronesNeeded(gameState);
    }

    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return LarvaBoundMacroHatchery.isLurkerTechReady(techProgression);
    }

    @Override
    public boolean needLair() {
        return true;
    }

    @Override
    public boolean needHive() {
        return true;
    }

    /**
     * Chitinous Plating and Anabolic Synthesis move ahead of the Ultralisk stream once
     * {@value #ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY} Ultralisks are alive.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Chitinous_Plating:
            case Anabolic_Synthesis:
                return new ArmyUpgradeTrigger(ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY, UnitType.Zerg_Ultralisk);
            default:
                return null;
        }
    }
}
