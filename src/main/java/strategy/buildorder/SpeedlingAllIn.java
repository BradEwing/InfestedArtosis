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
 * {@link #needHive()}. Zergling production is continuous and uncapped; the only cap is on drones,
 * following every hatch-tech ling build in the 2026-08-26 eight-bot survey. The drone target is 11,
 * split roughly 8 on minerals and 3 on the single Extractor, which is what funds Metabolic Boost.
 * The target is a floor as well as a ceiling: dead drones are replaced every frame, so the economy
 * is never cut to zero.
 *
 * <p><b>Exit decision: deliberate no-exit.</b> {@link #shouldTransition(GameState)} is overridden to
 * return false rather than inheriting it. An army-count or economy gate would hand the game back to
 * the Lair builds, and those are the arms that never win: across the 29 Aug - 3 Sep batches
 * 3HatchMuta is 0W-76L outside Tomas Cere, 2HatchMuta is 3W-92L and CrazyZerg is 11W-272L. Teching
 * out of this build is the failure mode it exists to avoid, so the build plays itself out, as
 * CherryPi's 5pool and PurpleWave's ZvE4Pool do. Reacting to a hard counter by leaving hatchery tech
 * altogether belongs to the build order switching mechanism in IA-251, not inside this class.
 *
 * <p><b>Evolution Chamber criterion.</b> The all-in is stalled once the game passes 8:00 with at
 * least 12 zerglings alive and Metabolic Boost finished: past the point a two hatch speedling flood
 * should have closed the game, still producing, and no longer competing with speed for gas. Only
 * then does the build take one Evolution Chamber and Zerg Melee Attacks. Melee attacks stop at
 * level 1 because {@link TechProgression#canPlanMeleeUpgrades()} requires a Lair for level 2, and
 * carapace is deliberately skipped: a second Evolution Chamber upgrade would make
 * {@link TechProgression#needLairForNextEvolutionChamberUpgrades()} true and pull a Lair in through
 * the back door.
 *
 * <p>Static defense and rush zerglings are left to {@link #planEmergencyDefense(GameState)} with the
 * base class defaults. Inflating {@link #zerglingsNeeded(GameState)} here would queue priority 1
 * zerglings ahead of drone replacement for as long as a rush lasts, which is the one way this build
 * could starve its own economy.
 */
public class SpeedlingAllIn extends BuildOrder {

    static final int DRONE_TARGET = 11;

    static final int HATCHERY_TARGET = 2;

    static final int MAX_HATCHERIES = 4;

    static final int MAX_QUEUED_ZERGLING_PLANS = 6;

    static final int STALL_ZERGLINGS = 12;

    static final Time STALL_TIME = new Time(8, 0);

    public SpeedlingAllIn() {
        super("SpeedlingAllIn");
    }

    /**
     * Deliberate no-exit. See the class comment: every candidate to transition into leaves hatchery
     * tech, which is what this build exists not to do.
     */
    @Override
    public boolean shouldTransition(GameState gameState) {
        return false;
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        int hatcheryTotal = gameState.hatcheryCount() + Math.max(0, gameState.getPlannedHatcheries());
        if (shouldPlanHatchery(hatcheryTotal, gameState.isFloatingMinerals())) {
            Plan hatcheryPlan = this.planMacroHatchery(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
                return plans;
            }
        }

        if (gameState.getBaseData().numExtractor() < 1 && gameState.canPlanExtractor()) {
            plans.add(this.planExtractor(gameState));
            return plans;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Metabolic_Boost)) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Metabolic_Boost));
            return plans;
        }

        if (shouldPlanDrone(gameState.numEconomyDrones(), gameState.canPlanDrone())) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Drone));
            return plans;
        }

        int zerglingCount = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        if (allInStalled(gameState.getGameTime(), zerglingCount, techProgression.isMetabolicBoost())) {
            plans.addAll(this.planStallUpgrades(gameState));
            if (!plans.isEmpty()) {
                return plans;
            }
        }

        int queuedZerglings = gameState.queuedUnitPlanCount(UnitType.Zerg_Zergling);
        if (shouldPlanZergling(queuedZerglings, techProgression.isSpawningPool())) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Zergling));
        }

        return plans;
    }

    private List<Plan> planStallUpgrades(GameState gameState) {
        List<Plan> plans = new ArrayList<>();
        TechProgression techProgression = gameState.getTechProgression();

        if (techProgression.evolutionChambers() < 1 && techProgression.canPlanEvolutionChamber()) {
            plans.add(this.planEvolutionChamber(gameState));
            return plans;
        }

        if (gameState.canPlanUpgrade(UpgradeType.Zerg_Melee_Attacks)) {
            plans.add(this.planUpgrade(gameState, UpgradeType.Zerg_Melee_Attacks));
        }

        return plans;
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }

    /**
     * True while the drone count is under the target. Reads gathering plus queued drones so a
     * scouting or building drone does not hold the target up, and so replacements are planned as
     * drones die.
     *
     * @param economyDrones gathering plus queued drones, from {@link GameState#numEconomyDrones()}
     * @param canPlanDrone the central economic gate, from {@link GameState#canPlanDrone()}
     */
    static boolean shouldPlanDrone(int economyDrones, boolean canPlanDrone) {
        return economyDrones < DRONE_TARGET && canPlanDrone;
    }

    /**
     * True while another hatchery is owed. Two hatcheries are the build; beyond that a hatchery is
     * only worth taking when minerals outrun the larva that can spend them.
     *
     * @param hatcheryTotal completed hatcheries plus hatcheries already queued
     */
    static boolean shouldPlanHatchery(int hatcheryTotal, boolean floatingMinerals) {
        if (hatcheryTotal < HATCHERY_TARGET) {
            return true;
        }
        return floatingMinerals && hatcheryTotal < MAX_HATCHERIES;
    }

    /**
     * True while another zergling plan should join the queue. There is no army cap: the only bound
     * is on how many plans wait in the queue at once, so production never stops on a count.
     */
    static boolean shouldPlanZergling(int queuedZerglingPlans, boolean poolComplete) {
        return poolComplete && queuedZerglingPlans < MAX_QUEUED_ZERGLING_PLANS;
    }

    /**
     * True once the all-in has failed to close the game but is still producing. Gates the Evolution
     * Chamber and melee attacks, which are the only upgrades this build takes past speed.
     */
    static boolean allInStalled(Time gameTime, int zerglingCount, boolean metabolicBoost) {
        return metabolicBoost && zerglingCount >= STALL_ZERGLINGS && gameTime.greaterThan(STALL_TIME);
    }
}
