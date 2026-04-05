package strategy.buildorder.terran;

import bwapi.TechType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import info.BaseData;
import info.GameState;
import info.TechProgression;
import macro.plan.Plan;
import util.Time;

import java.util.ArrayList;
import java.util.List;

/**
 * A variant of 3 Hatch Muta that skips Lurker tech entirely,
 * transitioning directly from Mutalisks into Hive tech with Ultralisks and Defilers.
 *
 * Key divergence: Evolution Chamber built simultaneously with Lair, immediately starting
 * +1 Carapace. All gas goes into early carapace upgrades and fast Hive tech.
 *
 * @see <a href="https://liquipedia.net/starcraft/3_Hatch_Muta_(vs._Terran)">Liquipedia</a>
 * @see <a href="https://tl.net/forum/brood-war/576159-crazy-zerg">TL.net</a>
 */
public class CrazyZerg extends TerranBase {

    private static final int DESIRED_MUTALISKS = 9;
    private static final int DESIRED_DEFILERS = 3;

    public CrazyZerg() {
        super("CrazyZerg");
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        List<Plan> plans = new ArrayList<>();

        TechProgression techProgression = gameState.getTechProgression();
        BaseData baseData = gameState.getBaseData();
        int baseCount = baseData.currentBaseCount();
        int extractorCount = baseData.numExtractor();
        int plannedHatcheries = gameState.getPlannedHatcheries();
        final int plannedAndCurrentHatcheries = plannedHatcheries + baseCount;
        int lairCount         = gameState.ourUnitCount(UnitType.Zerg_Lair);
        int hiveCount         = gameState.ourUnitCount(UnitType.Zerg_Hive);
        int spireCount        = gameState.ourUnitCount(UnitType.Zerg_Spire);
        int mutaCount         = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);
        int scourgeCount      = gameState.ourUnitCount(UnitType.Zerg_Scourge);
        int ultraliskCount    = gameState.ourUnitCount(UnitType.Zerg_Ultralisk);
        int defilerCount      = gameState.ourUnitCount(UnitType.Zerg_Defiler);
        int droneCount        = gameState.ourUnitCount(UnitType.Zerg_Drone);
        int zerglingCount     = gameState.ourUnitCount(UnitType.Zerg_Zergling);
        int overlordCount     = gameState.ourUnitCount(UnitType.Zerg_Overlord);
        int enemyVessel       = gameState.enemyUnitCount(UnitType.Terran_Science_Vessel);
        int enemyDropship     = gameState.enemyUnitCount(UnitType.Terran_Dropship);
        int enemyValkyrie     = gameState.enemyUnitCount(UnitType.Terran_Valkyrie);
        int enemyWraith       = gameState.enemyUnitCount(UnitType.Terran_Wraith);

        boolean hasLairOrHive = lairCount > 0 || hiveCount > 0;
        boolean hasHive = hiveCount > 0;

        boolean firstGas = gameState.canPlanExtractor() && techProgression.isSpawningPool() && extractorCount < 1;
        boolean secondGas = gameState.canPlanExtractor() && hasLairOrHive && extractorCount < 2;
        boolean thirdGas = gameState.canPlanExtractor() && baseCount >= 3 && hasLairOrHive && extractorCount < 3;
        boolean extraGas = gameState.canPlanExtractor() && baseCount > 3 && extractorCount < baseCount;

        boolean floatingMinerals = gameState.getGameTime().greaterThan(new Time(5, 0)) &&
                gameState.getResourceCount().availableMinerals() > ((plannedHatcheries + 1) * 350);

        boolean wantNatural = plannedAndCurrentHatcheries < 2 && droneCount >= 12;
        boolean wantThird   = plannedAndCurrentHatcheries < 3 && hasLairOrHive;
        boolean wantBaseAdvantage = behindOnBases(gameState) || floatingMinerals;

        boolean wantLair = gameState.canPlanLair() && lairCount < 1 && hiveCount < 1 && baseCount >= 2;
        boolean wantEvoChamber = techProgression.canPlanEvolutionChamber() && hasLairOrHive && techProgression.getEvolutionChambers() + techProgression.getPlannedEvolutionChambers() < 1;
        boolean wantSecondEvoChamber = techProgression.canPlanEvolutionChamber() && hasHive && techProgression.getEvolutionChambers() + techProgression.getPlannedEvolutionChambers() >= 1;
        boolean wantSpire = techProgression.canPlanSpire() && spireCount < 1 && hasLairOrHive && droneCount >= 16;

        boolean wantQueensNest = gameState.canPlanQueensNest() && mutaCount >= DESIRED_MUTALISKS && extractorCount >= 3;
        boolean wantHive = gameState.canPlanHive();
        boolean wantUltraliskCavern = gameState.canPlanUltraliskCavern();
        boolean wantDefilerMound = gameState.canPlanDefilerMound() && techProgression.isUltraliskCavern()
                && (extractorCount >= 4 || gameState.getGameTime().greaterThan(new Time(12, 0)));

        boolean wantMetabolicBoost = techProgression.canPlanMetabolicBoost() && hasLairOrHive && mutaCount >= 5;
        boolean wantCarapace = techProgression.canPlanCarapaceUpgrades() && techProgression.getEvolutionChambers() > 0;
        boolean wantMelee = techProgression.canPlanMeleeUpgrades() && techProgression.evolutionChambers() >= 2
                && gameState.getGameTime().greaterThan(new Time(10, 0));
        boolean wantFlyerAttack = mutaCount >= DESIRED_MUTALISKS
                && gameState.getGameTime().greaterThan(new Time(10, 0))
                && techProgression.canPlanFlyerAttack();
        boolean wantChitinousPlating = techProgression.canPlanChitinousPlating();
        boolean wantConsume = techProgression.canPlanConsume();
        boolean wantAdrenalGlands = techProgression.canPlanAdrenalGlands();
        boolean wantOverlordSpeed = needOverlordSpeed(gameState) && techProgression.canPlanOverlordSpeed();

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
                return plans;
            }
        }

        if (firstGas || secondGas || thirdGas || extraGas) {
            Plan extractorPlan = this.planExtractor(gameState);
            plans.add(extractorPlan);
        }

        if (spireCount > 0 && overlordCount < 4) {
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

        if (wantEvoChamber) {
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

        if (wantSecondEvoChamber) {
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

        if (droneCount < 15) {
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

        if (wantConsume) {
            Plan consumePlan = this.planTech(gameState, TechType.Consume);
            plans.add(consumePlan);
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
        if (techProgression.isSpire() && scourgeCount < desiredScourge) {
            plans.add(this.planUnit(gameState, UnitType.Zerg_Scourge));
            return plans;
        }

        if (techProgression.isSpire() && mutaCount < DESIRED_MUTALISKS) {
            Plan mutaliskPlan = this.planUnit(gameState, UnitType.Zerg_Mutalisk);
            plans.add(mutaliskPlan);
            return plans;
        }

        if (techProgression.isUltraliskCavern() && ultraliskCount < desiredUltralisks(gameState)) {
            Plan ultraliskPlan = this.planUnit(gameState, UnitType.Zerg_Ultralisk);
            plans.add(ultraliskPlan);
            return plans;
        }

        if (techProgression.isDefilerMound() && defilerCount < DESIRED_DEFILERS) {
            Plan defilerPlan = this.planUnit(gameState, UnitType.Zerg_Defiler);
            plans.add(defilerPlan);
            return plans;
        }

        final int desiredZerglings = this.zerglingsNeeded(gameState);
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

    private int dronesNeeded(GameState gameState) {
        int drones = 17;
        boolean hasLairOrHive = gameState.ourUnitCount(UnitType.Zerg_Lair) > 0
                || gameState.ourUnitCount(UnitType.Zerg_Hive) > 0;
        int hatchCount = gameState.ourUnitCount(UnitType.Zerg_Hatchery, UnitType.Zerg_Lair, UnitType.Zerg_Hive);

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
        boolean hasHive = gameState.ourUnitCount(UnitType.Zerg_Hive) > 0;
        int mutaCount = gameState.ourUnitCount(UnitType.Zerg_Mutalisk);

        if (mutaCount < DESIRED_MUTALISKS) {
            return super.zerglingsNeeded(gameState);
        }

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

    @Override
    public boolean needLair() {
        return true;
    }

    @Override
    public boolean needHive() {
        return true;
    }
}
