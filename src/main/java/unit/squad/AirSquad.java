package unit.squad;

import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import util.Time;

public class AirSquad extends Squad {

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
        updateCombatSimulator();
    }

    @Override
    public void removeUnit(ManagedUnit managedUnit) {
        super.removeUnit(managedUnit);
        updateCombatSimulator();
    }

    private void updateCombatSimulator() {
        if (hasOnly(UnitType.Zerg_Scourge)) {
            if (!(getCombatSimulator() instanceof ScourgeCombatSimulator)) {
                this.setCombatSimulator(new ScourgeCombatSimulator());
            }
        } else if (!(getCombatSimulator() instanceof HorizonCombatSimulator)) {
            this.setCombatSimulator(new HorizonCombatSimulator());
        }
    }
}
