package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import util.Time;

import java.util.ArrayList;
import java.util.List;

/**
 * Two hatchery speedling all-in, playable in every matchup.
 *
 * <p>Hatchery tech by definition: it never plans a Lair and never reports {@link #needLair()} or
 * {@link #needHive()}. Zergling production is continuous and uncapped; the cap is on drones, at 11,
 * split 8 on minerals and 3 on the single Extractor that funds Metabolic Boost once the natural is
 * up. The target is a floor as well as a ceiling, so dead drones are replaced and the economy is
 * never cut to zero. An opener that hands over above the target keeps its drones; nothing is cut.
 * It plans its own Spawning Pool when it does not have one, so it is reachable from an opener that
 * transitions before building one.
 *
 * <p>The second hatchery is the natural expansion; only once a second base is held do surplus
 * minerals buy macro hatcheries, up to {@link #MAX_HATCHERIES}, rather than banking. Expanding first
 * is what makes the drone target reachable: {@link GameState#canPlanDrone()} ceilings workers at
 * {@code bases * 7 + geysers * 3} against Zerg, which is 10 on one base and 17 on two.
 *
 * <p>Exit decision: this build deliberately does not transition out, so {@link #shouldTransition}
 * returns false rather than inheriting it. Leaving hatchery tech is the failure this build exists to
 * avoid; reacting to a hard counter belongs to the build order switching mechanism in IA-251.
 *
 * <p>Once stalled, meaning past {@link #STALL_TIME} with {@link #STALL_ZERGLINGS} zerglings alive and
 * Metabolic Boost finished, it takes one Evolution Chamber and Zerg Melee Attacks. Melee stops at
 * level 1 and carapace is skipped, because a second Evolution Chamber upgrade would make
 * {@link TechProgression#needLairForNextEvolutionChamberUpgrades()} true and pull in a Lair.
 *
 * <p>Static defense and rush zerglings are left to {@link #planEmergencyDefense(GameState)} with the
 * base class defaults.
 */
public class SpeedlingAllIn extends BuildOrder {

    static final int DRONE_TARGET = 11;

    static final int HATCHERY_TARGET = 2;

    static final int BASE_TARGET = 2;

    static final int MAX_HATCHERIES = 5;

    static final int SURPLUS_MINERALS = 300;

    static final int MAX_QUEUED_ZERGLING_PLANS = 6;

    static final int STALL_ZERGLINGS = 12;

    static final Time STALL_TIME = new Time(8, 0);

    /**
     * What the build owes next, in the order it owes it. A step that produces no plan falls through
     * to the one after it.
     */
    enum Step {
        SPAWNING_POOL,
        EXPANSION,
        MACRO_HATCHERY,
        EXTRACTOR,
        METABOLIC_BOOST,
        DRONE,
        STALL_UPGRADE,
        ZERGLING,
        NONE
    }

    /**
     * What the stall response owes next, once {@link #allInStalled} holds.
     */
    enum StallStep {
        EVOLUTION_CHAMBER,
        MELEE_ATTACKS,
        NONE
    }

    static final int STEP_COUNT = Step.NONE.ordinal();

    public SpeedlingAllIn() {
        super("SpeedlingAllIn");
    }

    @Override
    public boolean shouldTransition(GameState gameState) {
        return false;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        boolean[] gates = gates(gameState);
        for (Step step = nextStep(gates); step != Step.NONE; step = nextStep(gates)) {
            List<Plan> plans = attempt(gameState, step);
            if (!plans.isEmpty()) {
                return plans;
            }
            gates[step.ordinal()] = false;
        }
        return new ArrayList<>();
    }

    private boolean[] gates(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();
        int hatcheryTotal = gameState.hatcheryCount() + Math.max(0, gameState.getPlannedHatcheries());
        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int queuedZerglings = gameState.queuedUnitPlanCount(UnitType.Zerg_Zergling);

        boolean wantHatchery = shouldPlanHatchery(hatcheryTotal, gameState.getResourceCount().availableMinerals());

        boolean[] gates = new boolean[STEP_COUNT];
        gates[Step.SPAWNING_POOL.ordinal()] = techProgression.canPlanPool();
        gates[Step.EXPANSION.ordinal()] = shouldExpand(wantHatchery, gameState.getBaseData().currentAndReservedCount());
        gates[Step.MACRO_HATCHERY.ordinal()] = wantHatchery;
        gates[Step.EXTRACTOR.ordinal()] = gameState.getBaseData().numExtractor() < 1 && gameState.canPlanExtractor();
        gates[Step.METABOLIC_BOOST.ordinal()] = gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost);
        gates[Step.DRONE.ordinal()] = shouldPlanDrone(gameState.numEconomyDrones(), gameState.canPlanDrone());
        gates[Step.STALL_UPGRADE.ordinal()] = allInStalled(gameState.getGameTime(), zerglingCount,
                techProgression.isMetabolicBoost());
        gates[Step.ZERGLING.ordinal()] = shouldPlanZergling(queuedZerglings, techProgression.isSpawningPool());
        return gates;
    }

    private List<Plan> attempt(GameState gameState, Step step) {
        List<Plan> plans = new ArrayList<>();
        switch (step) {
            case SPAWNING_POOL:
                plans.add(this.planSpawningPool(gameState));
                return plans;
            case EXPANSION:
                Plan expansionPlan = this.planNewBase(gameState);
                if (expansionPlan != null) {
                    plans.add(expansionPlan);
                }
                return plans;
            case MACRO_HATCHERY:
                Plan hatcheryPlan = this.planMacroHatchery(gameState);
                if (hatcheryPlan != null) {
                    plans.add(hatcheryPlan);
                }
                return plans;
            case EXTRACTOR:
                plans.add(this.planExtractor(gameState));
                return plans;
            case METABOLIC_BOOST:
                plans.add(this.planUpgrade(gameState, UpgradeType.Metabolic_Boost));
                return plans;
            case DRONE:
                plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
                return plans;
            case STALL_UPGRADE:
                return this.planStallUpgrades(gameState);
            case ZERGLING:
                plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
                return plans;
            default:
                return plans;
        }
    }

    private List<Plan> planStallUpgrades(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();
        boolean wantEvolutionChamber = techProgression.evolutionChambers() < 1
                && techProgression.canPlanEvolutionChamber();

        switch (nextStallStep(wantEvolutionChamber, gameState.canPlanUpgrade(UpgradeType.Zerg_Melee_Attacks))) {
            case EVOLUTION_CHAMBER:
                plans.add(this.planEvolutionChamber(gameState));
                return plans;
            case MELEE_ATTACKS:
                plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Melee_Attacks));
                return plans;
            default:
                return plans;
        }
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    /**
     * The first step whose gate is open, or {@link Step#NONE}.
     */
    static Step nextStep(boolean[] gates) {
        for (int i = 0; i < STEP_COUNT; i++) {
            if (gates[i]) {
                return Step.values()[i];
            }
        }
        return Step.NONE;
    }

    static StallStep nextStallStep(boolean wantEvolutionChamber, boolean canPlanMeleeAttacks) {
        if (wantEvolutionChamber) {
            return StallStep.EVOLUTION_CHAMBER;
        }
        return canPlanMeleeAttacks ? StallStep.MELEE_ATTACKS : StallStep.NONE;
    }

    /**
     * @param economyDrones gathering plus queued drones, from {@link GameState#numEconomyDrones()}
     */
    static boolean shouldPlanDrone(int economyDrones, boolean canPlanDrone) {
        return economyDrones < DRONE_TARGET && canPlanDrone;
    }

    /**
     * Beyond the second hatchery the build is larva limited, not mineral limited, so unreserved
     * minerals are the signal to add one. {@link GameState#isFloatingMinerals()} is not that signal:
     * it wants more than 1050 banked at two hatcheries, a bar tuned for a macro economy that this
     * build banks its way to long before reacting.
     *
     * @param hatcheryTotal completed hatcheries plus hatcheries already queued
     * @param availableMinerals minerals mined and not reserved by a queued plan
     */
    /**
     * The hatchery we owe is the natural until we hold a second base, and a macro hatchery after
     * that. Expanding first also lifts {@link GameState#canPlanDrone()}, whose ceiling is
     * {@code bases * 7 + geysers * 3} against Zerg, from 10 workers to 17.
     *
     * @param baseCount bases owned plus bases reserved by a queued expansion
     */
    static boolean shouldExpand(boolean wantHatchery, int baseCount) {
        return wantHatchery && baseCount < BASE_TARGET;
    }

    static boolean shouldPlanHatchery(int hatcheryTotal, int availableMinerals) {
        if (hatcheryTotal < HATCHERY_TARGET) {
            return true;
        }
        return hatcheryTotal < MAX_HATCHERIES && availableMinerals >= SURPLUS_MINERALS;
    }

    /**
     * The bound is on zergling plans waiting in the queue, not on army size, so production never
     * stops on a count.
     */
    static boolean shouldPlanZergling(int queuedZerglingPlans, boolean poolComplete) {
        return poolComplete && queuedZerglingPlans < MAX_QUEUED_ZERGLING_PLANS;
    }

    /**
     * Stalled means the all-in failed to close the game but is still producing.
     */
    static boolean allInStalled(Time gameTime, int zerglingCount, boolean metabolicBoost) {
        return metabolicBoost && zerglingCount >= STALL_ZERGLINGS && gameTime.greaterThan(STALL_TIME);
    }
}
