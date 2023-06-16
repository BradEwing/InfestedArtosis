package info;

import bwapi.UpgradeType;
import lombok.Data;

@Data
public class TechProgression {

    // Buildings
    private boolean spawningPool = false;
    private boolean hydraliskDen = false;
    private boolean lair = false;
    private boolean spire = false;
    private boolean hive = false;
    private int evolutionChambers = 0;

    // Planned Buildings
    private boolean plannedSpawningPool = false;
    private boolean plannedDen = false;
    private boolean plannedLair = false;
    private boolean plannedSpire = false;
    private int plannedEvolutionChambers = 0;

    // Upgrades
    private boolean metabolicBoost = false;
    private boolean muscularAugments = false;
    private boolean groovedSpines = false;
    private int carapaceUpgrades = 0;
    private int rangedUpgrades = 0;
    private int meleeUpgrades = 0;
    private int flyerAttack = 0;
    private int flyerDefense = 0;

    // Planned Upgrades
    private boolean plannedMetabolicBoost = false;
    private boolean plannedMuscularAugments = false;
    private boolean plannedGroovedSpines = false;
    private boolean plannedCarapaceUpgrades = false;
    private boolean plannedRangedUpgrades = false;
    private boolean plannedMeleeUpgrades = false;
    private boolean plannedFlyerAttack = false;
    private boolean plannedFlyerDefense = false;

    public boolean canPlanSunkenColony() { return spawningPool; }

    public boolean canPlanExtractor() { return spawningPool || plannedSpawningPool; }

    public boolean canPlanPool() {
        return !plannedSpawningPool && !spawningPool;
    }

    public boolean canPlanHydraliskDen() {
        return spawningPool && !plannedDen && !hydraliskDen;
    }

    public boolean canPlanLair() {
        return spawningPool && !plannedLair && !lair;
    }

    public boolean canPlanSpire() {
        return lair && !plannedSpire && !spire;
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

    public boolean canPlanEvolutionChamber() { return spawningPool && plannedEvolutionChambers + evolutionChambers < 2; }

    // TODO: Code smell, all tech should probably be boolean here, and number of evolution chambers comes from BaseData/BuildingManager
    public int evolutionChambers() { return plannedEvolutionChambers + evolutionChambers; }

    public boolean canPlanCarapaceUpgrades() {
        if (plannedCarapaceUpgrades) {
            return false;
        }

        if (carapaceUpgrades == 0) {
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

    public void upgradeTech(UpgradeType u) {
        switch(u) {
            case Metabolic_Boost:
                metabolicBoost = true;
                plannedMetabolicBoost = false;
                break;
            case Muscular_Augments:
                muscularAugments = true;
                plannedMuscularAugments = false;
                break;
            case Grooved_Spines:
                groovedSpines = true;
                plannedGroovedSpines = false;
                break;
            case Zerg_Carapace:
                carapaceUpgrades += 1;
                plannedCarapaceUpgrades = false;
                break;
            case Zerg_Melee_Attacks:
                meleeUpgrades += 1;
                plannedMeleeUpgrades = false;
                break;
            case Zerg_Missile_Attacks:
                rangedUpgrades += 1;
                plannedRangedUpgrades = false;
                break;
            case Zerg_Flyer_Attacks:
                flyerAttack += 1;
                plannedFlyerAttack = false;
                break;
            case Zerg_Flyer_Carapace:
                flyerDefense += 1;
                plannedFlyerDefense = false;
                break;
        }
    }

}
