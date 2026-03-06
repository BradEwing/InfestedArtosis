package unit.squad;

import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import util.Time;

import java.util.HashMap;
import java.util.Map;

public class AirSquad extends Squad {

    private final Map<UnitType, Integer> composition = new HashMap<>();
    private int cachedSupply = 0;

    public AirSquad() {
        super();
        this.setCombatSimulator(new HorizonCombatSimulator());
        this.fightHysteresis = new Time(12);
        this.retreatHysteresis = new Time(36);
    }

    @Override
    public boolean isAirSquad() {
        return true;
    }

    @Override
    public void addUnit(ManagedUnit managedUnit) {
        super.addUnit(managedUnit);
        UnitType type = managedUnit.getUnitType();
        composition.merge(type, 1, Integer::sum);
        cachedSupply += type.supplyRequired();
        updateCombatSimulator();
    }

    @Override
    public void removeUnit(ManagedUnit managedUnit) {
        super.removeUnit(managedUnit);
        UnitType type = managedUnit.getUnitType();
        int count = composition.getOrDefault(type, 0) - 1;
        if (count <= 0) {
            composition.remove(type);
        } else {
            composition.put(type, count);
        }
        cachedSupply = Math.max(0, cachedSupply - type.supplyRequired());
        updateCombatSimulator();
    }

    public int getSupply() {
        return cachedSupply;
    }

    public Map<UnitType, Integer> getComposition() {
        return composition;
    }

    public int getCountOf(UnitType type) {
        return composition.getOrDefault(type, 0);
    }

    public boolean hasOnly(UnitType type) {
        return composition.size() == 1 && composition.containsKey(type);
    }

    private void updateCombatSimulator() {
        if (hasOnly(UnitType.Zerg_Scourge)) {
            this.setCombatSimulator(new ScourgeCombatSimulator());
        } else {
            if (!(getCombatSimulator() instanceof HorizonCombatSimulator)) {
                this.setCombatSimulator(new HorizonCombatSimulator());
            }
        }
    }
}
