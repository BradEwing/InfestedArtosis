package unit.managed;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;

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
        // Our fight target is no longer visible, drop in favor of fight target position
        if (defendTarget != null && defendTarget.getType() == UnitType.Unknown) {
            defendTarget = null;
        }
        if (defendTarget != null) {
            unit.attack(defendTarget.getPosition());
        }
    }
}
