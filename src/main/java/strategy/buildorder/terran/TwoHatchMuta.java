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
        boolean wantThird    = plannedAndCurrentHatcheries < 3 && spireCount > 0 && mutaCount > 5;
        boolean wantBaseAdvantage = behindOnBases(gameState) || floatingMinerals;

        // Lair timing
        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && baseCount >= 2;

        // Spire timing
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && lairCount >= 1 && droneCount >= 16;

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && !techProgression.isMetabolicBoost() && lairCount > 0;
        boolean wantFlyingAttack = shouldPlanFlyerAttack(techProgression, livingMutaCount);
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
            Plan expansionPlan = this.planNewBase(gameState);
            if (expansionPlan != null) {
                plans.add(expansionPlan);
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
                        gameState.queuedUnitPlanCount(UnitType.Zerg_Mutalisk), gameState.getUnitTypeCount());
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
     * <p>The count read against the target includes plans already charged to it, so a wave is
     * queued one plan at a time until the target is met. While a Mutalisk plan still waits in the
     * queue no second one is added, and the build goes on to plan the units below it.
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
        if (!shouldPlanMutalisk(techProgression, count.get(UnitType.Zerg_Mutalisk), desiredMutalisks, gatherers)) {
            return new ArrayList<>();
        }
        return planAdvancedUnit(UnitType.Zerg_Mutalisk, techProgression, gatherers, queuedMutalisks, count);
    }
}
