package strategy.buildorder.terran;

import bwapi.Race;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.Readiness;
import info.TechProgression;
import info.UnitTypeCount;
import macro.Reactions;
import macro.plan.Plan;
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Liquipedia Overview: The 2 Hatch Muta build can be a useful variation as many Terrans are comfortable countering
 * standard 3 Hatch Muta timings. However, this build requires excellent Mutalisk micro so you can effectively pick off
 * Marines and deal damage to your opponent. When playing a 2 Hatch, regardless of the ultimate variation, aggression
 * is key. 2 Hatch Mutas aim to take complete map control save for Terran's one timing push before your Hive tech is out.
 * If map control is lost due to loss of units, generally the Zerg has lost.
 *
 * @see <a href="https://liquipedia.net/starcraft/2_Hatch_Muta_(vs._Terran)">Liquipedia</a>
 */
public class TwoHatchMuta extends TerranBase {

    static final int MUTALISKS_BEFORE_FLYER_UPGRADE = 7;

    static final int MACRO_HATCHERY_HATCHERY_CAP = 4;

    /** Gatherers that must stand before Flyer Attacks level 2 or 3 is queued. Tuning constant. */
    static final int WORKERS_BEFORE_LATER_FLYER_UPGRADES = 22;

    public TwoHatchMuta() {
        super("2HatchMuta");
    }

    @Override
    protected List<Plan> buildPlans(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount         = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int committedLairs    = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Lair);
        int spireCount        = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spire);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int livingMutaCount   = gameState.ourLivingUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int droneCount        = gameState.numEconomyDrones();
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int overlordCount     = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int enemyVessel = gameState.enemyUnitCount(UnitType.Terran_Science_Vessel);
        int enemyDropship = gameState.enemyUnitCount(UnitType.Terran_Dropship);
        int enemyValkyrie = gameState.enemyUnitCount(UnitType.Terran_Valkyrie);
        int enemyWraith = gameState.enemyUnitCount(UnitType.Terran_Wraith);

        // Gas timing
        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && committedLairs > 0;

        // Check for floating resources
        boolean floatingMinerals = gameState.isFloatingMinerals();

        // Base timing
        boolean wantNatural  = plannedAndCurrentHatcheries < 2 && droneCount >= 12;
        int basesHeldOrReserved = baseData.currentAndReservedCount();
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && spireCount > 0 && mutaCount > 5
                || LurkerDefilerUltraTransition.wantsThirdBase(gameState.getGameTime(), baseCount, basesHeldOrReserved);
        boolean wantBaseAdvantage = wantsBaseAdvantage(behindOnBases(gameState), floatingMinerals,
                gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spire) > 0,
                gameState.getResourceCount().availableMinerals()
                        - gameState.getProductionQueue().advancedUnitMineralDemand(),
                gameState.getResourceCount().availableGas()
                        - gameState.getProductionQueue().advancedUnitGasDemand(),
                gameState.floatingMineralsBar(), firstWaveMutalisks(gameState));

        // Lair timing
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;

        // Spire timing
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1 && droneCount >= 16;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;
        boolean wantFlyingAttack = shouldPlanFlyerAttack(techProgression, livingMutaCount, gameState.numGatherers(),
                laterFlyerUpgradeWorkerFloor(gameState.workerHardCap(), dronesNeeded(gameState)));
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantFlyingAttack);

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
        if (wantNatural || wantThird || wantBaseAdvantage) {
            Plan expansionPlan = this.planNewBase(gameState,
                    LurkerDefilerUltraTransition.prefersGasBase(basesHeldOrReserved));
            if (expansionPlan != null) {
                plans.add(expansionPlan);
            }
        }

        if (expansionPlanned(plans) == null && wantsReleasedMacroHatchery(
                gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spire) > 0,
                firstWaveMutalisks(gameState) - gameState.queuedUnitPlanCount(UnitType.Zerg_Mutalisk),
                gameState.hatcheryCount() + gameState.inFlightHatcheryPlans()
                        + gameState.hatcheriesUnderConstruction(true) + gameState.hatcheriesUnderConstruction(false),
                gameState.inFlightHatcheryPlans(true) + gameState.hatcheriesUnderConstruction(true),
                gameState.knownEnemyMobileGroundCombatUnitsAtOurBases())) {
            Plan macroHatcheryPlan = this.planMacroHatcheryAt(gameState, baseData.getMainBase());
            if (macroHatcheryPlan != null) {
                plans.add(macroHatcheryPlan);
            }
        }

        if (firstGas || secondGas) {
            Plan extractorPlan = this.planExtractor(gameState);
            plans.add(extractorPlan);
        }


        if (shouldPlanOverlord(spireCount, overlordCount, gameState.hasExcessSupply())) {
            Plan overlordPlan = this.planUnit(gameState, UnitType.Zerg_Overlord);
            plans.add(overlordPlan);
            return plans;
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

        // Plan Upgrades
        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        if (wantFlyingAttack) {
            Plan flyingCarapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Flyer_Attacks);
            plans.add(flyingCarapacePlan);
        }

        // Plan Overlord Speed
        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        // Plan Units
        final int desiredScourge = enemyVessel + enemyDropship + enemyValkyrie + enemyWraith;
        if (techProgression.isSpire() && scourgeCount < desiredScourge && canPlanAdvancedUnit(gameState, UnitType.Zerg_Scourge)) {
            List<Plan> scourgePlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Scourge);
            if (!scourgePlans.isEmpty()) {
                plans.addAll(scourgePlans);
                return plans;
            }
        }

        final int desiredMutalisks = desiredMutalisks(gameState);
        List<Plan> mutaliskPlans = withheldByDroneRound(gameState.getDroneRound().isActive(), UnitType.Zerg_Mutalisk)
                ? new ArrayList<>()
                : planMutalisk(techProgression, desiredMutalisks, gameState.numGatherers(),
                        gameState.queuedUnitPlanCount(UnitType.Zerg_Mutalisk), gameState.getUnitTypeCount(),
                        gameState.numLarva(), gameState.getResourceCount().availableMinerals(),
                        gameState.getResourceCount().availableGas());
        if (!mutaliskPlans.isEmpty()) {
            plans.addAll(mutaliskPlans);
            return plans;
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (zerglingCount < desiredZerglings) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            plans.add(zerglingPlan);
            return plans;
        }

        int desiredDrones = this.dronesNeeded(gameState);
        if (plans.isEmpty() && droneCount < desiredDrones) {
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

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return Collections.singleton(UnitType.Zerg_Mutalisk);
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return dronesNeeded(gameState);
    }

    @Override
    protected boolean holdsFirstWaveBank(GameState gameState) {
        return ownsFirstWaveBank(gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Spire) > 0,
                firstWaveMutalisks(gameState));
    }

    @Override
    protected int macroHatcheryGasBar(GameState gameState) {
        return macroHatcheryGasBar(gameState.getTechProgression().isSpire(), firstWaveMutalisks(gameState),
                gameState.hatcheryCount());
    }

    /**
     * Mutalisks produced plus Mutalisk plans not yet finished. Losses do not lower it.
     */
    private static int firstWaveMutalisks(GameState gameState) {
        return gameState.totalProduced(UnitType.Zerg_Mutalisk)
                + gameState.outstandingUnitPlanCount(UnitType.Zerg_Mutalisk);
    }

    /**
     * The unreserved gas the macro hatchery gate counts as floating.
     *
     * <p>Once the first wave is queued the gas goes to one Mutalisk at a time and the bank rarely
     * floats gas, so the hatchery the first wave deferred is asked for on minerals alone until the
     * build holds {@link #MACRO_HATCHERY_HATCHERY_CAP} hatcheries.
     *
     * @param spireReady whether a Spire is finished
     * @param mutaliskCount Mutalisks produced plus Mutalisk plans not yet finished
     * @param hatcheries completed larva-producing hatcheries
     * @return zero for the released request, else {@link LarvaBoundMacroHatchery#FLOAT_GAS}
     */
    static int macroHatcheryGasBar(boolean spireReady, int mutaliskCount, int hatcheries) {
        if (spireReady && !ownsFirstWaveBank(true, mutaliskCount) && hatcheries < MACRO_HATCHERY_HATCHERY_CAP) {
            return 0;
        }
        return LarvaBoundMacroHatchery.FLOAT_GAS;
    }

    /**
     * Whether the build plans the macro hatchery the first wave deferred.
     *
     * <p>Asked once the first wave no longer owns the bank, and not conditional on the bank floating:
     * continuous Mutalisk production spends minerals as they arrive, so the floating-bank request rarely
     * fires. The first wave's Mutalisks must all be scheduled or produced first, because a hatchery plan
     * that cannot yet pay for itself holds the build-ahead slot and blocks every Mutalisk behind it.
     *
     * @param spireCommitted whether a Spire is finished, morphing or planned
     * @param scheduledWaveMutalisks Mutalisks produced plus Mutalisk plans past the queue
     * @param hatcheriesAndOnTheWay completed larva-producing hatcheries plus every hatchery plan in flight
     *     and hatchery under construction, of both kinds
     * @param outstandingMacroHatcheries macro hatchery plans in flight plus macro hatcheries under construction
     * @param enemiesAtBases enemy mobile ground combat units last known at our bases
     * @return true when fewer than {@link #MACRO_HATCHERY_HATCHERY_CAP} hatcheries stand or are coming, no macro
     *     hatchery is outstanding and no enemy is at our bases
     */
    static boolean wantsReleasedMacroHatchery(boolean spireCommitted, int scheduledWaveMutalisks,
                                              int hatcheriesAndOnTheWay, int outstandingMacroHatcheries,
                                              int enemiesAtBases) {
        return spireCommitted && !ownsFirstWaveBank(spireCommitted, scheduledWaveMutalisks)
                && hatcheriesAndOnTheWay < MACRO_HATCHERY_HATCHERY_CAP && outstandingMacroHatcheries == 0
                && enemiesAtBases == 0;
    }

    private static Plan expansionPlanned(List<Plan> plans) {
        for (Plan plan : plans) {
            if (plan.getPlannedUnit() == UnitType.Zerg_Hatchery) {
                return plan;
            }
        }
        return null;
    }

    /**
     * Whether the first Mutalisk wave still owns the unreserved bank.
     *
     * @param spireCommitted whether a Spire is finished, morphing or planned
     * @param mutaliskCount Mutalisks living and queued
     * @return true while a Spire is committed and fewer than {@link #MUTALISKS_BEFORE_FLYER_UPGRADE} are counted
     */
    static boolean ownsFirstWaveBank(boolean spireCommitted, int mutaliskCount) {
        return spireCommitted && mutaliskCount < MUTALISKS_BEFORE_FLYER_UPGRADE;
    }

    @Override
    protected boolean holdsCalmEconomyRound(GameState gameState) {
        return holdsFirstWave(gameState.structureCount(Readiness.STANDING, UnitType.Zerg_Spire) > 0,
                gameState.totalProduced(UnitType.Zerg_Mutalisk));
    }

    /**
     * Whether the first Mutalisk wave still outranks a calm-economy round.
     *
     * @param spireStanding whether a Spire is morphing or finished
     * @param mutalisksProduced Mutalisks produced so far, living and lost
     * @return true while a Spire stands and fewer than {@link #MUTALISKS_BEFORE_FLYER_UPGRADE} have been produced
     */
    static boolean holdsFirstWave(boolean spireStanding, int mutalisksProduced) {
        return spireStanding && mutalisksProduced < MUTALISKS_BEFORE_FLYER_UPGRADE;
    }

    protected int dronesNeeded(GameState gameState) {
        int drones = 17;
        int lairCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair);
        int hatchCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hatchery);
        if (lairCount > 0) {
            drones += 6;
        }
        if (hatchCount > 2) {
            drones += 6 * (hatchCount - 1);
        }
        return drones;
    }

    private int desiredMutalisks(GameState gameState) {
        TechProgression techProgression = gameState.getTechProgression();

        if (!techProgression.isSpire()) {
            return 0;
        }

        int baseTarget = 11;
        
        // Increase mutalisk target when floating minerals (similar to ThreeHatchLurker hydralisks)
        int availableMinerals = gameState.getResourceCount().availableMinerals();
        if (availableMinerals > 400) {
            int extraMutalisks = availableMinerals / 100;
            baseTarget += Math.min(extraMutalisks, 20);
        }

        return baseTarget;
    }

    @Override
    protected boolean macroHatcheryTechReady(TechProgression techProgression) {
        return LarvaBoundMacroHatchery.isSpireReady(techProgression);
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
     * Hands over to {@link LurkerDefilerUltra} once enemy Goliaths shut the Mutalisks out or the
     * clock runs past them, and the economy gate in {@link LurkerDefilerUltraTransition} is met.
     */
    @Override
    public boolean shouldTransition(GameState gameState) {
        return LurkerDefilerUltraTransition.shouldEnter(gameState, getName(),
                LurkerDefilerUltraTransition.twoHatchMutaTrigger(gameState.enemyUnitCount(UnitType.Terran_Goliath),
                        gameState.getGameTime()));
    }

    @Override
    public Set<BuildOrder> transition(GameState gameState) {
        return LurkerDefilerUltraTransition.candidates();
    }

    static boolean shouldPlanOverlord(int spireCount, int overlordCount, boolean excessSupply) {
        return spireCount > 1 && overlordCount < 4 && !excessSupply;
    }

    /**
     * Whether the build should queue the next Flyer Attacks level.
     *
     * <p>Reads completed Mutalisks only. A planned Mutalisk has not been given larva or gas yet,
     * and the upgrade waits on neither larva nor a morph, so counting plans starts it against the
     * bank those Mutalisks still need. The same gate holds each level, so the next level is not
     * queued on the frame the previous one finishes unless the Mutalisks stand.
     *
     * @param techProgression the tech state, which already bars a level in flight or one the
     *     current tech cannot research
     * @param livingMutalisks completed Mutalisks
     * @return true when the next Flyer Attacks level should be queued
     */
    static boolean shouldPlanFlyerAttack(TechProgression techProgression, int livingMutalisks) {
        return livingMutalisks >= MUTALISKS_BEFORE_FLYER_UPGRADE && techProgression.canPlanFlyerAttack();
    }

    /**
     * Whether the build should queue the next Flyer Attacks level, holding every level after the
     * first until the worker floor of {@link #laterFlyerUpgradeWorkerFloor} gather. A queued level
     * holds the bank at the upgrade priority, which outranks every Drone plan.
     *
     * @param techProgression the tech state
     * @param livingMutalisks completed Mutalisks
     * @param gatherers workers gathering minerals or gas
     * @param workerFloor gatherers a level after the first needs
     * @return true when the next Flyer Attacks level should be queued
     */
    static boolean shouldPlanFlyerAttack(TechProgression techProgression, int livingMutalisks, int gatherers,
                                         int workerFloor) {
        if (techProgression.getFlyerAttack() > 0 && gatherers < workerFloor) {
            return false;
        }
        return shouldPlanFlyerAttack(techProgression, livingMutalisks);
    }

    /**
     * @param workerHardCap workers past which the worker gates want no Drone
     * @param dronesNeeded the build's Drone target
     * @return {@value #WORKERS_BEFORE_LATER_FLYER_UPGRADES}, cut to stay under the build's Drone target and
     *     the hard cap so the floor can always be met
     */
    static int laterFlyerUpgradeWorkerFloor(int workerHardCap, int dronesNeeded) {
        return Math.min(WORKERS_BEFORE_LATER_FLYER_UPGRADES, Math.min(workerHardCap, dronesNeeded) - 1);
    }

    /**
     * Flyer Attacks moves ahead of the Mutalisk stream once the
     * {@value #MUTALISKS_BEFORE_FLYER_UPGRADE} Mutalisks that plan it are alive.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        if (upgradeType == UpgradeType.Zerg_Flyer_Attacks) {
            return new ArmyUpgradeTrigger(MUTALISKS_BEFORE_FLYER_UPGRADE, UnitType.Zerg_Mutalisk);
        }
        return null;
    }

    static boolean shouldPlanMutalisk(TechProgression techProgression, int mutaCount, int desiredMutalisks, int gatherers) {
        return techProgression.isSpire() && mutaCount < desiredMutalisks
                && canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, techProgression, gatherers);
    }

    /**
     * The Mutalisk plan for this frame, ranked ahead of the Drone and Zergling backlog.
     *
     * <p>The count read against the target includes plans already charged to it. This overload has
     * no larva or bank to size a wave by, so it queues one plan at a time until the target is met.
     * While a Mutalisk plan still waits in the queue no second one is added, and the build goes on
     * to plan the units below it.
     *
     * @param techProgression the bot's tech state
     * @param desiredMutalisks the Mutalisk target
     * @param gatherers workers gathering, for the eligibility gate
     * @param queuedMutalisks Mutalisk plans still waiting in the production queue
     * @param count the unit counts, including planned units, that the plan is charged to
     * @return one Mutalisk plan at the advanced unit priority, or none
     */
    static List<Plan> planMutalisk(TechProgression techProgression, int desiredMutalisks, int gatherers,
                                   int queuedMutalisks, UnitTypeCount count) {
        return planMutalisk(techProgression, desiredMutalisks, gatherers, queuedMutalisks, count, 0, 0, 0);
    }

    /**
     * The Mutalisk plans for this frame, sized to the larva and bank while the first wave is
     * still being issued.
     *
     * <p>Below {@link #MUTALISKS_BEFORE_FLYER_UPGRADE} Mutalisks counted, the build queues as many
     * plans as there are free larva and Mutalisks the unreserved bank pays for, less the plans
     * already waiting, so the Drone and Zergling backlog cannot take the larva the Spire frees.
     * It queues at least one when none waits. From the seventh Mutalisk on it is one plan at a
     * time.
     *
     * @param larva free larva not yet handed to a plan
     * @param availableMinerals minerals mined and not reserved by a scheduled plan
     * @param availableGas gas mined and not reserved by a scheduled plan
     * @see #planMutalisk(TechProgression, int, int, int, UnitTypeCount)
     */
    static List<Plan> planMutalisk(TechProgression techProgression, int desiredMutalisks, int gatherers,
                                   int queuedMutalisks, UnitTypeCount count, int larva, int availableMinerals,
                                   int availableGas) {
        List<Plan> plans = new ArrayList<>();
        int wanted = mutalisksToQueue(count.get(UnitType.Zerg_Mutalisk), queuedMutalisks, larva,
                availableMinerals, availableGas);
        for (int i = 0; i < wanted; i++) {
            if (!shouldPlanMutalisk(techProgression, count.get(UnitType.Zerg_Mutalisk), desiredMutalisks, gatherers)) {
                break;
            }
            plans.addAll(planAdvancedUnit(UnitType.Zerg_Mutalisk, techProgression, gatherers, 0, count));
        }
        return plans;
    }

    /**
     * How many Mutalisk plans to add this frame.
     *
     * @param mutaliskCount Mutalisks counted, living and planned
     * @param queuedMutalisks Mutalisk plans waiting in the queue
     * @return for the first wave, the larva and bank bound less the queued plans, and at least one
     *     when none waits; from the seventh Mutalisk on, one when none waits and zero when one does
     */
    static int mutalisksToQueue(int mutaliskCount, int queuedMutalisks, int larva, int availableMinerals,
                                int availableGas) {
        if (mutaliskCount >= MUTALISKS_BEFORE_FLYER_UPGRADE) {
            return queuedMutalisks > 0 ? 0 : 1;
        }
        int affordable = Math.min(availableMinerals / UnitType.Zerg_Mutalisk.mineralPrice(),
                availableGas / UnitType.Zerg_Mutalisk.gasPrice());
        int room = Math.min(larva, affordable) - queuedMutalisks;
        int wave = Math.min(room, MUTALISKS_BEFORE_FLYER_UPGRADE - mutaliskCount);
        if (queuedMutalisks == 0) {
            return Math.max(wave, 1);
        }
        return Math.max(wave, 0);
    }

    /**
     * Whether the build asks for an expansion beyond its natural and third.
     *
     * <p>Falling behind the enemy on bases always asks. Floating minerals ask on the unreserved
     * bank once a Spire is committed only after the queued advanced unit plans and the Mutalisks
     * still short of {@value #MUTALISKS_BEFORE_FLYER_UPGRADE} that the unreserved gas pays for are
     * covered. Minerals the gas cannot turn into Mutalisks are not held, and the hold is gone for
     * good once seven Mutalisks are counted.
     *
     * @param behindOnBases whether the enemy holds more bases
     * @param floatingMinerals whether unreserved minerals sit above the float bar
     * @param spireCommitted whether a Spire is finished, morphing or planned
     * @param mineralsAfterQueuedDemand unreserved minerals less queued advanced unit plans
     * @param gasAfterQueuedDemand unreserved gas less queued advanced unit plans
     * @param floatBar the unreserved minerals that count as floating
     * @param mutaliskCount Mutalisks produced plus Mutalisk plans not yet finished
     * @return true when the build should plan a new base
     */
    static boolean wantsBaseAdvantage(boolean behindOnBases, boolean floatingMinerals, boolean spireCommitted,
                                      int mineralsAfterQueuedDemand, int gasAfterQueuedDemand, int floatBar,
                                      int mutaliskCount) {
        if (behindOnBases) {
            return true;
        }
        if (!ownsFirstWaveBank(spireCommitted, mutaliskCount)) {
            return floatingMinerals;
        }
        int gasBoundMutalisks = Math.max(0, gasAfterQueuedDemand) / UnitType.Zerg_Mutalisk.gasPrice();
        int firstWaveShortfall = Math.min(Math.max(0, MUTALISKS_BEFORE_FLYER_UPGRADE - mutaliskCount), gasBoundMutalisks);
        int firstWaveCost = firstWaveShortfall * UnitType.Zerg_Mutalisk.mineralPrice();
        return floatingMinerals && mineralsAfterQueuedDemand - firstWaveCost > floatBar;
    }
}
