package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

/**
 * One of our units inside one engagement. The arrival offset from the engagement start is the
 * raw trickle signal; everything else here exists to let an analysis script qualify it.
 */
@Getter
class EngagementUnit {

    private final int unitId;
    private final UnitType unitType;
    private final int arrivalFrame;
    private final int hitPointsAtArrival;
    private final String roleAtArrival;
    private final String squadAtArrival;

    private int exitFrame;
    private int hitPointsAtExit;
    private boolean died;
    private int deathFrame = -1;
    private int deathX = -1;
    private int deathY = -1;

    EngagementUnit(int unitId, UnitType unitType, int arrivalFrame, int hitPoints, String roleAtArrival, String squadAtArrival) {
        this.unitId = unitId;
        this.unitType = unitType;
        this.arrivalFrame = arrivalFrame;
        this.hitPointsAtArrival = hitPoints;
        this.roleAtArrival = roleAtArrival;
        this.squadAtArrival = squadAtArrival;
        this.exitFrame = arrivalFrame;
        this.hitPointsAtExit = hitPoints;
    }

    void observe(int frame, int hitPoints) {
        this.exitFrame = frame;
        this.hitPointsAtExit = hitPoints;
    }

    void markDied(int frame, Position position) {
        this.died = true;
        this.deathFrame = frame;
        if (position != null) {
            this.deathX = position.getX();
            this.deathY = position.getY();
        }
    }

    int getSupply() {
        return unitType.supplyRequired();
    }
}
