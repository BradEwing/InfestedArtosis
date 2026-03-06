package unit.squad;

import unit.squad.horizon.HorizonCombatSimulator;

public class GroundSquad extends Squad {

    public GroundSquad() {
        super();
        this.setCombatSimulator(new HorizonCombatSimulator());
    }

    @Override
    public boolean isGroundSquad() {
        return true;
    }
}
