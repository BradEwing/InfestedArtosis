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
import strategy.buildorder.ArmyUpgradeTrigger;
import strategy.buildorder.LarvaBoundMacroHatchery;
import util.Time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A variant of 3 Hatch Muta that skips Lurker tech entirely,
 * transitioning directly from Mutalisks into Hive tech with Ultralisks.
 *
 * Key divergence: Evolution Chamber built simultaneously with Lair, immediately starting
 * +1 Carapace. All gas goes into early carapace upgrades and fast Hive tech.
 *
 * @see <a href="https://liquipedia.net/starcraft/3_Hatch_Muta_(vs._Terran)">Liquipedia</a>
 * @see <a href="https://tl.net/forum/brood-war/576159-crazy-zerg">TL.net</a>
 */
public class CrazyZerg extends TerranBase {

    private static final int MUTALISK_CAP = 9;
    private static final int DESIRED_DEFILERS = 3;
    private static final int ULTRALISK_THRESHOLD_FOR_MUTA_REPLENISH = 3;
    static final int ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY = 3;

    public CrazyZerg() {
        super("CrazyZerg");
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
        int hiveCount         = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hive);
        int spireCount        = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Spire);
        int committedLairOrHiveCount = gameState.structureCount(Readiness.COMMITTED, UnitType.Zerg_Lair, UnitType.Zerg_Hive);
        int mutaCountLiving         = gameState.ourLivingUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int ultraliskCount    = gameState.ourUnitCount(UnitType.Zerg_Ultralisk);
        int defilerCount      = gameState.ourUnitCount(UnitType.Zerg_Defiler);
        int droneCount        = gameState.numEconomyDrones();
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int overlordCount     = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int enemyVessel       = gameState.enemyUnitCount(UnitType.Terran_Science_Vessel);
        int enemyDropship     = gameState.enemyUnitCount(UnitType.Terran_Dropship);
        int enemyValkyrie     = gameState.enemyUnitCount(UnitType.Terran_Valkyrie);
        int enemyWraith       = gameState.enemyUnitCount(UnitType.Terran_Wraith);

        boolean hasLairOrHive = lairCount > 0 || hiveCount > 0;
        boolean committedLairOrHive = committedLairOrHiveCount > 0;
        boolean hasHive = hiveCount > 0;

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && committedLairOrHive && extractorCount < 2;
        boolean thirdGas = gameState.canPlanExtractor() && baseCount >= 3 && committedLairOrHive && extractorCount < 3;
        boolean extraGas = gameState.canPlanExtractor() && baseCount > 3 && extractorCount < baseCount;

        boolean floatingMinerals = gameState.isFloatingMinerals();

        boolean wantNatural = plannedAndCurrentHatcheries < 2 && droneCount >= 12;
        boolean wantThird   = plannedAndCurrentHatcheries < 3 && droneCount >= 14
                && (hasLairOrHive || gameState.getResourceCount().availableMinerals() >= 350);
        boolean wantBaseAdvantage = behindOnBases(gameState) || floatingMinerals;

        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && hiveCount < 1 && baseCount >= 2;
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && hasLairOrHive && droneCount >= 16;

        boolean wantQueensNest = wantGasBoundHiveTech(gameState, UnitType.Zerg_Queens_Nest,
                gameState.canPlanQueensNest());
        boolean wantHive = gameState.canPlanHive();
        boolean wantUltraliskCavern = gameState.canPlanUltraliskCavern();
        boolean wantDefilerMound = wantGasBoundHiveTech(gameState, UnitType.Zerg_Defiler_Mound,
                techProgression.canPlanDefilerMound());

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && hasLairOrHive;
        boolean wantCarapace = techProgression.canPlanCarapaceUpgrades() && techProgression.getEvolutionChambers() > 0;
        boolean wantMelee = techProgression.canPlanMeleeUpgrades() && techProgression.evolutionChambers() >= 2
                && gameState.getGameTime().greaterThan(new Time(10, 0));
        boolean wantFlyerAttack = mutaCountLiving >= MUTALISK_CAP
                && gameState.getGameTime().greaterThan(new Time(10, 0))
                && techProgression.canPlanFlyerAttack();
        boolean wantChitinousPlating = techProgression.canPlanChitinousPlating();
        boolean wantAnabolicSynthesis = techProgression.canPlanAnabolicSynthesis()
                && techProgression.isChitinousPlating();
        boolean wantConsume = techProgression.canPlanConsume();
        boolean wantPlague = techProgression.canPlanPlague();
        boolean wantAdrenalGlands = techProgression.canPlanAdrenalGlands();
        boolean wantOverlordSpeed = shouldPlanOverlordSpeed(needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed(),
                Reactions.isAirOrCloakThreatSeen(gameState),
                wantCarapace, wantMelee, wantFlyerAttack, wantChitinousPlating, wantAnabolicSynthesis, wantAdrenalGlands);

        final int desiredSunkenColonies = this.requiredSunkens(gameState);
        if (!gameState.basesNeedingSunken(desiredSunkenColonies).isEmpty()) {
            plans.addAll(this.planSunkenColony(gameState));
        }

        final int desiredSporeColonies = this.requiredSpores(gameState);
        if (!gameState.basesNeedingSpore(desiredSporeColonies).isEmpty()) {
            plans.addAll(this.planSporeColony(gameState));
        }

        if (wantNatural || wantThird || wantBaseAdvantage) {
            Plan hatcheryPlan = this.planNewBase(gameState);
            if (hatcheryPlan != null) {
                plans.add(hatcheryPlan);
            }
        }

        if (firstGas || secondGas || thirdGas || extraGas) {
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

        if (hasLairOrHive && shouldPlanUpgradeEvolutionChamber(techProgression, 1)) {
            Plan evoPlan = this.planEvolutionChamber(gameState);
            plans.add(evoPlan);
        }

        if (wantSpire) {
            Plan spirePlan = this.planSpire(gameState);
            plans.add(spirePlan);
            return plans;
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

        if (hasHive && shouldPlanUpgradeEvolutionChamber(techProgression, 2)) {
            Plan evoPlan = this.planEvolutionChamber(gameState);
            plans.add(evoPlan);
        }

        if (wantUltraliskCavern) {
            Plan ultraCavernPlan = this.planUltraliskCavern(gameState);
            plans.add(ultraCavernPlan);
            return plans;
        }

        if (wantDefilerMound) {
            Plan defilerMoundPlan = this.planDefilerMound(gameState);
            plans.add(defilerMoundPlan);
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
        if (shouldDroneBeforeZerglings(droneCount, zerglingCount, desiredZerglings)) {
            Plan dronePlan = this.planUnit(gameState, UnitType.Zerg_Drone);
            plans.add(dronePlan);
            return plans;
        }

        if (wantMetabolicBoost) {
            Plan metabolicBoostPlan = this.planUpgrade(gameState, UpgradeType.Metabolic_Boost);
            plans.add(metabolicBoostPlan);
        }

        if (wantCarapace) {
            Plan carapacePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Carapace);
            plans.add(carapacePlan);
        }

        if (wantMelee) {
            Plan meleePlan = this.planUpgrade(gameState, UpgradeType.Zerg_Melee_Attacks);
            plans.add(meleePlan);
        }

        if (wantFlyerAttack) {
            Plan flyerAttackPlan = this.planUpgrade(gameState, UpgradeType.Zerg_Flyer_Attacks);
            plans.add(flyerAttackPlan);
        }

        if (wantChitinousPlating) {
            Plan chitinousPlan = this.planUpgrade(gameState, UpgradeType.Chitinous_Plating);
            plans.add(chitinousPlan);
        }

        if (wantAnabolicSynthesis) {
            Plan anabolicPlan = this.planUpgrade(gameState, UpgradeType.Anabolic_Synthesis);
            plans.add(anabolicPlan);
        }

        if (wantConsume) {
            Plan consumePlan = this.planTech(gameState, TechType.Consume);
            plans.add(consumePlan);
        }

        if (wantPlague) {
            Plan plaguePlan = this.planTech(gameState, TechType.Plague);
            plans.add(plaguePlan);
        }

        if (wantAdrenalGlands) {
            Plan adrenalPlan = this.planUpgrade(gameState, UpgradeType.Adrenal_Glands);
            plans.add(adrenalPlan);
        }

        if (wantOverlordSpeed) {
            Plan overlordSpeedPlan = this.planUpgrade(gameState, UpgradeType.Pneumatized_Carapace);
            plans.add(overlordSpeedPlan);
        }

        final int desiredScourge = enemyVessel + enemyDropship + enemyValkyrie + enemyWraith;
        if (techProgression.isSpire() && scourgeCount < desiredScourge && canPlanAdvancedUnit(gameState, UnitType.Zerg_Scourge)) {
            List<Plan> scourgePlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Scourge);
            if (!scourgePlans.isEmpty()) {
                plans.addAll(scourgePlans);
                return plans;
            }
        }

        int totalMutasProduced = gameState.totalProduced(UnitType.Zerg_Mutalisk);
        int mutaCount = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int livingUltralisks = gameState.ourLivingUnitCount(UnitType.Zerg_Ultralisk);
        boolean reachedMutaCap = totalMutasProduced >= MUTALISK_CAP;
        boolean canReplenishMutas = livingUltralisks >= ULTRALISK_THRESHOLD_FOR_MUTA_REPLENISH;
        boolean wantMoreMutas = mutaCount < MUTALISK_CAP && (!reachedMutaCap || canReplenishMutas);
        if (shouldPlanMutalisk(techProgression, wantMoreMutas, gameState.numGatherers())) {
            List<Plan> mutaliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Mutalisk);
            if (!mutaliskPlans.isEmpty()) {
                plans.addAll(mutaliskPlans);
                return plans;
            }
        }

        if (techProgression.isUltraliskCavern() && ultraliskCount < desiredUltralisks(gameState)
                && canPlanAdvancedUnit(gameState, UnitType.Zerg_Ultralisk)) {
            List<Plan> ultraliskPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Ultralisk);
            if (!ultraliskPlans.isEmpty()) {
                plans.addAll(ultraliskPlans);
                return plans;
            }
        }

        if (techProgression.isDefilerMound() && defilerCount < DESIRED_DEFILERS && canPlanAdvancedUnit(gameState, UnitType.Zerg_Defiler)) {
            List<Plan> defilerPlans = this.planAdvancedUnit(gameState, UnitType.Zerg_Defiler);
            if (!defilerPlans.isEmpty()) {
                plans.addAll(defilerPlans);
                return plans;
            }
        }

        if (zerglingCount < desiredZerglings) {
            Plan zerglingPlan = this.planUnit(gameState, UnitType.Zerg_Zergling);
            plans.add(zerglingPlan);
            return plans;
        }

        int desiredDrones = dronesNeeded(gameState);
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

    private int desiredUltralisks(GameState gameState) {
        int availableGas = gameState.getResourceCount().availableGas();
        int baseTarget = 4;
        if (availableGas > 400) {
            baseTarget += Math.min(availableGas / 200, 4);
        }
        return baseTarget;
    }

    @Override
    protected Set<UnitType> droneRoundArmy() {
        return Collections.singleton(UnitType.Zerg_Mutalisk);
    }

    @Override
    protected int droneRoundDroneCap(GameState gameState) {
        return dronesNeeded(gameState);
    }

    private int dronesNeeded(GameState gameState) {
        int drones = 17;
        boolean hasLairOrHive = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Lair, UnitType.Zerg_Hive) > 0;
        int hatchCount = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);

        if (hasLairOrHive) {
            drones += 6;
        }
        if (hatchCount > 2) {
            drones += 6 * (hatchCount - 2);
        }
        return drones;
    }

    @Override
    protected int zerglingsNeeded(GameState gameState) {
        boolean hasHive = gameState.structureCount(Readiness.USABLE, UnitType.Zerg_Hive) > 0;
        int base = super.zerglingsNeeded(gameState);

        if (hasHive) {
            base = Math.max(base, 24);
            int availableMinerals = gameState.getResourceCount().availableMinerals();
            if (availableMinerals > 300) {
                base += Math.min(availableMinerals / 50, 40);
            }
        }

        if (gameState.getTechProgression().isAdrenalGlands()) {
            base = Math.max(base, 36);
        }

        return Math.min(base, 80);
    }

    /**
     * The Spire, as for every Mutalisk build. The composition goes on to Ultralisks and Defilers,
     * but the Spire is the first tech whose units the build spends every larva it has on, so it is
     * the point from which floating banks read as a larva limit rather than as tech being saved
     * for.
     */
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

    static boolean shouldPlanOverlord(int spireCount, int overlordCount, boolean excessSupply) {
        return spireCount > 0 && overlordCount < 4 && !excessSupply;
    }

    static boolean shouldPlanMutalisk(TechProgression techProgression, boolean wantMoreMutas, int gatherers) {
        return techProgression.isSpire() && wantMoreMutas && canPlanAdvancedUnit(UnitType.Zerg_Mutalisk, techProgression, gatherers);
    }

    /**
     * Flyer Attacks moves ahead of the Mutalisk stream once the {@value #MUTALISK_CAP} Mutalisks
     * that plan it are alive, and Chitinous Plating and Anabolic Synthesis move ahead of the
     * Ultralisk stream once {@value #ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY} Ultralisks are.
     */
    @Override
    protected ArmyUpgradeTrigger armyUpgradeTrigger(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Zerg_Flyer_Attacks:
                return new ArmyUpgradeTrigger(MUTALISK_CAP, UnitType.Zerg_Mutalisk);
            case Chitinous_Plating:
            case Anabolic_Synthesis:
                return new ArmyUpgradeTrigger(ULTRALISKS_BEFORE_ULTRALISK_UPGRADE_PRIORITY, UnitType.Zerg_Ultralisk);
            default:
                return null;
        }
    }
}
