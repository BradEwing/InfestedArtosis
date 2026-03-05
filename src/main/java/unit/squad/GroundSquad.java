package unit.squad;

import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.HashMap;
import java.util.Map;

public class GroundSquad extends Squad {

    private final Map<UnitType, Integer> composition = new HashMap<>();
    private int cachedSupply = 0;

    public GroundSquad() {
        super();
        this.setCombatSimulator(new HorizonCombatSimulator());
    }

    @Override
    public boolean isGroundSquad() {
        return true;
    }

    @Override
    public void addUnit(ManagedUnit managedUnit) {
        super.addUnit(managedUnit);
        UnitType type = managedUnit.getUnitType();
        composition.merge(type, 1, Integer::sum);
        cachedSupply += type.supplyRequired();
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
}
