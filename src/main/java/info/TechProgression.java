package info;

import lombok.Data;

@Data
public class TechProgression {

    // Buildings
    private boolean spawningPool = false;
    private boolean hydraliskDen = false;
    private boolean lair = false;
    private boolean spire = false;

    // Planned Buildings
    private boolean plannedSpawningPool = false;
    private boolean plannedDen = false;
    private boolean plannedLair = false;
    private boolean plannedSpire = false;

    // Upgrades
    private boolean metabolicBoost = false;
    private boolean muscularAugments = false;
    private boolean groovedSpines = false;

    // Planned Upgrades
    private boolean plannedMetabolicBoost = false;
    private boolean plannedMuscularAugments = false;
    private boolean plannedGroovedSpines = false;

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

}
