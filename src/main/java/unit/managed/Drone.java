package unit.managed;

import bwapi.Game;
import bwapi.Unit;

public class Drone extends ManagedUnit {

    public Drone(Game game, Unit unit, UnitRole role) {
        super(game, unit, role);
    }

    @Override
    protected void gather() {
        if (!this.hasNewGatherTarget & (unit.isGatheringMinerals() || unit.isGatheringGas())) return;
        if (!isReady) return;

        if (unit.isCarrying()) {
            setUnready();
            unit.returnCargo();
            return;
        }

        if (gatherTarget == null) {
            role = UnitRole.IDLE;
            return;
        }

        setUnready();
        unit.gather(gatherTarget);
        hasNewGatherTarget = false;
    }

    @Override
    protected void defend() {
        if (!isReady) {
            return;
        }
        if (unit.isAttackFrame()) {
            return;
        }
        setUnready();

        if (defendTarget != null) {
            unit.attack(defendTarget.getPosition());
        }
    }
}
