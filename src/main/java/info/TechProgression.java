package info;

import bwapi.UnitType;
import bwapi.UpgradeType;
import lombok.Data;

@Data
public class TechProgression {

    // Buildings
    private boolean spawningPool = false;
    private boolean hydraliskDen = false;
    private boolean lair = false;
    private boolean spire = false;
    private boolean queensNest = false;
    private boolean hive = false;
    private boolean ultraliskCavern = false;
    private boolean defilerMound = false;
    private int evolutionChambers = 0;

    // Planned Buildings
    private boolean plannedSpawningPool = false;
    private boolean plannedDen = false;
    private boolean plannedLair = false;
    private boolean plannedSpire = false;
    private boolean plannedQueensNest = false;
    private boolean plannedHive = false;
    private boolean plannedUltraliskCavern = false;
    private boolean plannedDefilerMound = false;
    private int plannedEvolutionChambers = 0;

    // Upgrades
    private boolean lurker = false;
    private boolean metabolicBoost = false;
    private boolean muscularAugments = false;
    private boolean groovedSpines = false;
    private int carapaceUpgrades = 0;
    private int rangedUpgrades = 0;
    private int meleeUpgrades = 0;
    private int flyerAttack = 0;
    private int flyerDefense = 0;
    private boolean overlordSpeed = false;
    private boolean chitinousPlating = false;
    private boolean anabolicSynthesis = false;
    private boolean adrenalGlands = false;
    private boolean consume = false;
    private boolean plague = false;
    private boolean plannedOverlordSpeed = false;

    // Planned Upgrades
    private boolean plannedLurker = false;
    private boolean plannedMetabolicBoost = false;
    private boolean plannedMuscularAugments = false;
    private boolean plannedGroovedSpines = false;
    private boolean plannedCarapaceUpgrades = false;
    private boolean plannedRangedUpgrades = false;
    private boolean plannedMeleeUpgrades = false;
    private boolean plannedFlyerAttack = false;
    private boolean plannedFlyerDefense = false;
    private boolean plannedChitinousPlating = false;
    private boolean plannedAnabolicSynthesis = false;
    private boolean plannedAdrenalGlands = false;
    private boolean plannedConsume = false;
    private boolean plannedPlague = false;

    public boolean canPlanSunkenColony() {
        return spawningPool;
    }

    public boolean canPlanSporeColony() {
        return evolutionChambers > 0;
    }

    public boolean canPlanExtractor() { 
        return spawningPool || plannedSpawningPool; 
    }

    public boolean canPlanPool() {
        return !plannedSpawningPool && !spawningPool;
    }

    public boolean canPlanHydraliskDen() {
        return spawningPool && !plannedDen && !hydraliskDen;
    }

    public boolean canPlanLair() {
        return spawningPool && !plannedLair && !lair;
    }

    public boolean canPlanHive() {
        return queensNest && lair && !plannedHive && !hive;
    }

    public boolean canPlanSpire() {
        return lair && !plannedSpire && !spire;
    }

    public boolean canPlanQueensNest() { 
        return lair && !plannedQueensNest && !queensNest; 
    }

    public boolean canPlanUltraliskCavern() {
        return hive && !plannedUltraliskCavern && !ultraliskCavern;
    }

    public boolean canPlanDefilerMound() {
        return hive && !plannedDefilerMound && !defilerMound;
    }

    public boolean canPlanChitinousPlating() {
        return ultraliskCavern && !plannedChitinousPlating && !chitinousPlating;
    }

    public boolean canPlanAnabolicSynthesis() {
        return ultraliskCavern && !plannedAnabolicSynthesis && !anabolicSynthesis;
    }

    public boolean canPlanAdrenalGlands() {
        return spawningPool && hive && !plannedAdrenalGlands && !adrenalGlands;
    }

    public boolean canPlanConsume() {
        return defilerMound && !plannedConsume && !consume;
    }

    public boolean canPlanPlague() {
        return defilerMound && !plannedPlague && !plague;
    }

    public boolean canPlanMetabolicBoost() {
        return spawningPool && !plannedMetabolicBoost && !metabolicBoost;
    }

    public boolean canPlanMuscularAugments() {
        return hydraliskDen && !plannedMuscularAugments && !muscularAugments;
    }

    public boolean canPlanGroovedSpines() {
        return hydraliskDen && !plannedGroovedSpines && !groovedSpines;
    }

    public boolean canPlanLurker() { 
        return hydraliskDen && lair && !plannedLurker && !lurker; 
    }

    public boolean canPlanEvolutionChamber() { 
        return spawningPool && plannedEvolutionChambers + evolutionChambers < 2; 
    }

    // TODO: Code smell, all tech should probably be boolean here, and number of evolution chambers comes from BaseData/BuildingManager
    public int evolutionChambers() { 
        return plannedEvolutionChambers + evolutionChambers; 
    }

    public boolean canPlanCarapaceUpgrades() {
        if (plannedCarapaceUpgrades) {
            return false;
        }

        if (evolutionChambers == 0) {
            return false;
        }

        if (carapaceUpgrades >= 3) {
            return false;
        }

        if (carapaceUpgrades == 2 && !hive) {
            return false;
        }

        if (carapaceUpgrades == 1 && !lair) {
            return false;
        }

        return true;
    }

    public boolean canPlanRangedUpgrades() {
        if (plannedRangedUpgrades) {
            return false;
        }

        if (evolutionChambers == 0) {
            return false;
        }

        if (rangedUpgrades >= 3) {
            return false;
        }

        if (rangedUpgrades == 2 && !hive) {
            return false;
        }

        if (rangedUpgrades == 1 && !lair) {
            return false;
        }

        return true;
    }

    public boolean canPlanMeleeUpgrades() {
        if (plannedMeleeUpgrades) {
            return false;
        }

        if (evolutionChambers == 0) {
            return false;
        }

        if (meleeUpgrades >= 3) {
            return false;
        }

        if (meleeUpgrades == 2 && !hive) {
            return false;
        }

        if (meleeUpgrades == 1 && !lair) {
            return false;
        }

        return true;
    }

    public boolean canPlanFlyerAttack() {
        if (plannedFlyerAttack) {
            return false;
        }

        if (!spire) {
            return false;
        }

        if (flyerAttack >= 3) {
            return false;
        }

        if (flyerAttack == 2 && !hive) {
            return false;
        }

        return true;
    }

    public boolean canPlanFlyerDefense() {
        if (plannedFlyerDefense) {
            return false;
        }

        if (!spire) {
            return false;
        }

        if (flyerDefense >= 3) {
            return false;
        }

        if (flyerDefense == 2 && !hive) {
            return false;
        }

        return true;
    }

    public boolean canPlanOverlordSpeed() {
        return !plannedOverlordSpeed && !overlordSpeed;
    }

    public int evolutionChamberBuffer() {
        int buffer = 0;
        if (plannedCarapaceUpgrades) {
            buffer += UpgradeType.Zerg_Carapace.upgradeTime();
        }
        if (plannedRangedUpgrades) {
            buffer += UpgradeType.Zerg_Missile_Attacks.upgradeTime();
        }
        if (plannedMeleeUpgrades) {
            buffer += UpgradeType.Zerg_Melee_Attacks.upgradeTime();
        }

        return buffer;
    }

    public boolean needLairForNextEvolutionChamberUpgrades() {
        return meleeUpgrades + rangedUpgrades + carapaceUpgrades > 1;
    }

    // TODO: Adrenal glands
    public boolean needHiveForUpgrades() {
        return needHiveForNextEvolutionChamberUpgrades() || needHiveForSpireUpgrades();
    }

    private boolean needHiveForSpireUpgrades() {
        return flyerAttack + flyerDefense > 3;
    }

    private boolean needHiveForNextEvolutionChamberUpgrades() {
        return meleeUpgrades + rangedUpgrades + carapaceUpgrades > 3;
    }

    /**
     * The building an upgrade is researched at.
     *
     * @param upgradeType the upgrade
     * @return the building, or null when the upgrade has none tracked here
     */
    public static UnitType prerequisiteForUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case Metabolic_Boost:
            case Adrenal_Glands:
                return UnitType.Zerg_Spawning_Pool;
            case Muscular_Augments:
            case Grooved_Spines:
                return UnitType.Zerg_Hydralisk_Den;
            case Zerg_Carapace:
            case Zerg_Missile_Attacks:
            case Zerg_Melee_Attacks:
                return UnitType.Zerg_Evolution_Chamber;
            case Zerg_Flyer_Attacks:
            case Zerg_Flyer_Carapace:
                return UnitType.Zerg_Spire;
            case Pneumatized_Carapace:
                return UnitType.Zerg_Lair;
            case Chitinous_Plating:
            case Anabolic_Synthesis:
                return UnitType.Zerg_Ultralisk_Cavern;
            default:
                return null;
        }
    }

    /**
     * True when at least one {@link #prerequisiteForUpgrade} building for the upgrade has finished,
     * so the upgrade can be researched without waiting on a building plan.
     *
     * @param upgradeType the upgrade
     * @return false when the building is only planned or morphing, or the upgrade has none tracked
     */
    public boolean isUpgradePrerequisiteComplete(UpgradeType upgradeType) {
        UnitType prerequisite = prerequisiteForUpgrade(upgradeType);
        if (prerequisite == null) {
            return false;
        }
        switch (prerequisite) {
            case Zerg_Spawning_Pool:
                return spawningPool;
            case Zerg_Hydralisk_Den:
                return hydraliskDen;
            case Zerg_Evolution_Chamber:
                return evolutionChambers > 0;
            case Zerg_Spire:
                return spire;
            case Zerg_Lair:
                return lair;
            case Zerg_Ultralisk_Cavern:
                return ultraliskCavern;
            default:
                return false;
        }
    }

}
