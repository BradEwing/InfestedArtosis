package info;

import bwapi.Player;
import bwapi.UnitType;
import bwapi.UpgradeType;

/**
 * Tracks resources: minerals, gas and supply
 *
 * Used for production planning and predictions of future game state.
 *
 * TODO: Track supply, possibly larva?
 */
public class ResourceCount {

    // Income prediction constants
    // Adapted from PurpleWave: https://github.com/dgant/PurpleWave/blob/master/src/Information/Counting/Accounting.scala#L12
    final double mineralsPerFramePerWorker = 0.044;
    final double gasPerFramePerWorker = 0.069;

    private Player self;
    private int reservedMinerals = 0;
    private int reservedGas = 0;
    private int reservedLarva = 0;

    public ResourceCount(Player self) {
        this.self = self;
    }

    public void reserveUnit(UnitType unit) {
        this.reservedMinerals += unit.mineralPrice();
        this.reservedGas += unit.gasPrice();

        if (!unit.isBuilding()) {
            reservedLarva += 1;
        }
    }

    public void unreserveUnit(UnitType unit) {
        reservedMinerals -= unit.mineralPrice();
        reservedGas -= unit.gasPrice();

        if (!unit.isBuilding()) {
            reservedLarva -= 1;
        }
    }

    private int availableMinerals() { return self.minerals() - reservedMinerals; }

    private int availableGas() { return self.gas() - reservedGas; }

    private boolean canAfford(int mineralPrice, int gasPrice) { return availableMinerals() < mineralPrice || availableGas() < gasPrice; }

    public boolean canAffordUnit(UnitType unit) {
        final int mineralPrice = unit.mineralPrice();
        final int gasPrice = unit.gasPrice();
        return canAfford(mineralPrice, gasPrice);
    }

    public void reserveUpgrade(UpgradeType upgrade) {
        this.reservedMinerals += upgrade.mineralPrice();
        this.reservedGas += upgrade.gasPrice();
    }

    public void unreserveUpgrade(UpgradeType upgrade) {
        reservedMinerals -= upgrade.mineralPrice();
        reservedGas -= upgrade.gasPrice();
    }

    public boolean canAffordUpgrade(UpgradeType upgrade) {
        final int mineralPrice = upgrade.mineralPrice();
        final int gasPrice = upgrade.gasPrice();
        return canAfford(mineralPrice, gasPrice);
    }

    public boolean canAffordHatch(int plannedHatcheries) {
        return availableMinerals() > ((1 + plannedHatcheries) * 300);
    }

    public boolean needExtractor() {
        return availableMinerals() - availableGas() > 200;
    }

    /**
     * Predict the frame when the unit can be built.
     *
     * Return a frame in the infinite future if resource need cannot be met.
     *
     * @param unit unitType
     * @param currentFrame game's current frame
     * @param mineralWorkers number of workers on minerals
     * @param gasWorkers number of workers on gas
     * @return frame when all resources will be available
     */
    public int frameCanAffordUnit(UnitType unit, int currentFrame, int mineralWorkers, int gasWorkers) {
        //if (this.canAffordUnit(unit)) { return currentFrame; }

        final int mineralsNeeded = unit.mineralPrice() + reservedMinerals - self.minerals();
        final int gasNeeded = unit.gasPrice() + reservedGas - self.gas();

        int framesToGather = 0;
        if (mineralsNeeded > 0) {
            if (mineralWorkers == 0) { return Integer.MAX_VALUE; }
            double mineralsPerFrame = mineralsPerFramePerWorker * mineralWorkers;
            int neededFrames = (int) Math.round(mineralsNeeded / mineralsPerFrame);
            if (neededFrames > framesToGather) {
                framesToGather = neededFrames;
            }
        }
        if (gasNeeded > 0) {
            if (gasWorkers == 0) { return Integer.MAX_VALUE; }
            double gasPerFrame = gasPerFramePerWorker * gasWorkers;
            int neededFrames = (int) Math.round(gasNeeded / gasPerFrame);
            if (neededFrames > framesToGather) {
                framesToGather = neededFrames;
            }
        }

        // Buffer by 10 frames
        return currentFrame + framesToGather + 20;
    }

    public boolean canScheduleLarva(int currentLarva) {
        return currentLarva > reservedLarva;
    }

    public int frameCanAffordUpgrade() {
        return 0;
    }
}
