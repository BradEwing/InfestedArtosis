package info;

import bwapi.Player;
import bwapi.TechType;
import bwapi.UnitType;
import macro.plan.Plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks resources: minerals, gas and supply
 *
 * Used for production planning and predictions of future game state.
 *
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
    private int plannedSupply;

    private final Map<String, int[]> reservationLedger = new LinkedHashMap<>();
    private String lastClampWarning = null;

    public ResourceCount(Player self) {
        this.self = self;
    }

    public void reserveUnit(UnitType unit) {
        this.reservedMinerals += unit.mineralPrice();
        this.reservedGas += unit.gasPrice();
        addToLedger(unit.toString(), unit.mineralPrice(), unit.gasPrice());

        if (shouldReserveLarva(unit)) {
            reservedLarva += 1;
        }
    }

    public void unreserveUnit(UnitType unit) {
        int prevMinerals = reservedMinerals;
        int prevGas = reservedGas;
        reservedMinerals = Math.max(0, reservedMinerals - unit.mineralPrice());
        reservedGas = Math.max(0, reservedGas - unit.gasPrice());
        boolean clamped = prevMinerals - unit.mineralPrice() < 0 || prevGas - unit.gasPrice() < 0;
        removeFromLedger(unit.toString(), unit.mineralPrice(), unit.gasPrice(), clamped);

        if (shouldReserveLarva(unit)) {
            reservedLarva = Math.max(0, reservedLarva - 1);
        }
    }

    private boolean shouldReserveLarva(UnitType unit) {
        return !unit.isBuilding() && unit != UnitType.Zerg_Lurker;
    }

    public int availableMinerals() { 
        return self.minerals() - reservedMinerals; 
    }

    public int availableGas() { 
        return self.gas() - reservedGas; 
    }

    private boolean cannotAfford(int mineralPrice, int gasPrice) { 
        return availableMinerals() < mineralPrice || availableGas() < gasPrice; 
    }

    public boolean cannotAffordUnit(UnitType unit) {
        final int mineralPrice = unit.mineralPrice();
        final int gasPrice = unit.gasPrice();
        return cannotAfford(mineralPrice, gasPrice);
    }

    public void reserveUpgrade(Plan plan) {
        this.reservedMinerals += plan.mineralPrice();
        this.reservedGas += plan.gasPrice();
        addToLedger(plan.getName(), plan.mineralPrice(), plan.gasPrice());
    }

    public void unreserveUpgrade(Plan plan) {
        int prevMinerals = reservedMinerals;
        int prevGas = reservedGas;
        this.reservedMinerals = Math.max(0, reservedMinerals - plan.mineralPrice());
        this.reservedGas = Math.max(0, reservedGas - plan.gasPrice());
        boolean clamped = prevMinerals - plan.mineralPrice() < 0 || prevGas - plan.gasPrice() < 0;
        removeFromLedger(plan.getName(), plan.mineralPrice(), plan.gasPrice(), clamped);
    }

    public void reserveTechResearch(TechType techType) {
        this.reservedMinerals += techType.mineralPrice();
        this.reservedGas += techType.gasPrice();
        addToLedger(techType.toString(), techType.mineralPrice(), techType.gasPrice());
    }

    public void unreserveTechResearch(TechType techType) {
        int prevMinerals = reservedMinerals;
        int prevGas = reservedGas;
        this.reservedMinerals = Math.max(0, reservedMinerals - techType.mineralPrice());
        this.reservedGas = Math.max(0, reservedGas - techType.gasPrice());
        boolean clamped = prevMinerals - techType.mineralPrice() < 0 || prevGas - techType.gasPrice() < 0;
        removeFromLedger(techType.toString(), techType.mineralPrice(), techType.gasPrice(), clamped);
    }

    public boolean cannotAffordUpgrade(Plan plan) {
        return cannotAfford(plan.mineralPrice(), plan.gasPrice());
    }

    public boolean cannotAffordResearch(TechType techType) {
        final int mineralPrice = techType.mineralPrice();
        final int gasPrice = techType.gasPrice();
        return cannotAfford(mineralPrice, gasPrice);
    }

    public boolean needExtractor() {
        return this.isFloatingMinerals();
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
        //if (this.cannotAffordUnit(unit)) { return currentFrame; }

        final int mineralsNeeded = unit.mineralPrice() + reservedMinerals - self.minerals();
        final int gasNeeded = unit.gasPrice() + reservedGas - self.gas();

        int framesToGather = 0;
        if (mineralsNeeded > 0) {
            if (mineralWorkers == 0) { 
                return Integer.MAX_VALUE; 
            }
            double mineralsPerFrame = mineralsPerFramePerWorker * mineralWorkers;
            int neededFrames = (int) Math.round(mineralsNeeded / mineralsPerFrame);
            if (neededFrames > framesToGather) {
                framesToGather = neededFrames;
            }
        }
        if (gasNeeded > 0) {
            if (gasWorkers == 0) { 
                return Integer.MAX_VALUE; 
            }
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

    public boolean isFloatingMinerals() { 
        return availableMinerals() - availableGas() > 100; 
    }

    public boolean isFloatingGas() { 
        return availableGas() - availableMinerals() > 150; 
    }

    public int getPlannedSupply() {
        return plannedSupply;
    }

    public void setPlannedSupply(int s) {
        plannedSupply = s;
    }

    public int getReservedMinerals() {
        return reservedMinerals;
    }

    public int getReservedGas() {
        return reservedGas;
    }

    public Map<String, int[]> getReservationLedger() {
        return Collections.unmodifiableMap(reservationLedger);
    }

    public String getLastClampWarning() {
        return lastClampWarning;
    }

    private void addToLedger(String name, int minerals, int gas) {
        int[] entry = reservationLedger.get(name);
        if (entry == null) {
            reservationLedger.put(name, new int[]{minerals, gas});
        } else {
            entry[0] += minerals;
            entry[1] += gas;
        }
    }

    private void removeFromLedger(String name, int minerals, int gas, boolean clamped) {
        int[] entry = reservationLedger.get(name);
        if (entry == null) {
            if (minerals > 0 || gas > 0) {
                setClampWarning("Double unreserve (no entry): " + name);
            }
            return;
        }
        entry[0] -= minerals;
        entry[1] -= gas;
        if (entry[0] <= 0 && entry[1] <= 0) {
            reservationLedger.remove(name);
        } else {
            entry[0] = Math.max(0, entry[0]);
            entry[1] = Math.max(0, entry[1]);
        }
        if (clamped) {
            setClampWarning("Double unreserve: " + name);
        }
    }

    private void setClampWarning(String warning) {
        lastClampWarning = warning;
    }
}
