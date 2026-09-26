package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

/**
 * One of our units inside one engagement. The arrival offset from the engagement start is the
 * raw trickle signal; everything else here exists to let an analysis script qualify it.
 *
 * <p>The attack columns separate a unit that never reached the enemy from one that fought untouched. They are read
 * at each sample and once more when the unit dies, so an attack started just before its death is counted, while an
 * attack started after the last sample of a unit that leaves alive is not. The first attack frame is the last attack
 * the unit had started by the first reading that saw any, at most one sample interval after the true first attack.
 */
@Getter
class EngagementUnit {

    private final int unitId;
    private final UnitType unitType;
    private final int arrivalFrame;
    private final int hitPointsAtArrival;
    private final String roleAtArrival;
    private final String squadAtArrival;
    private final int attacksAtArrival;

    private int exitFrame;
    private int hitPointsAtExit;
    private int attacksAtExit;
    private int firstAttackFrame = -1;
    private boolean died;
    private int deathFrame = -1;
    private int deathX = -1;
    private int deathY = -1;

    EngagementUnit(int unitId, UnitType unitType, int arrivalFrame, int hitPoints, String roleAtArrival,
                   String squadAtArrival, int attacksStarted) {
        this.unitId = unitId;
        this.unitType = unitType;
        this.arrivalFrame = arrivalFrame;
        this.hitPointsAtArrival = hitPoints;
        this.roleAtArrival = roleAtArrival;
        this.squadAtArrival = squadAtArrival;
        this.attacksAtArrival = attacksStarted;
        this.exitFrame = arrivalFrame;
        this.hitPointsAtExit = hitPoints;
        this.attacksAtExit = attacksStarted;
    }

    /**
     * @param attacksStarted attacks the unit has started since it was first managed
     * @param lastAttackStartFrame frame of the last of those attacks
     */
    void observe(int frame, int hitPoints, int attacksStarted, int lastAttackStartFrame) {
        this.exitFrame = frame;
        this.hitPointsAtExit = hitPoints;
        observeAttacks(attacksStarted, lastAttackStartFrame);
    }

    /**
     * @param attacksStarted attacks the unit has started since it was first managed
     * @param lastAttackStartFrame frame of the last of those attacks
     */
    void observeAttacks(int attacksStarted, int lastAttackStartFrame) {
        this.attacksAtExit = attacksStarted;
        if (firstAttackFrame < 0 && attacksStarted > attacksAtArrival) {
            firstAttackFrame = lastAttackStartFrame;
        }
    }

    /**
     * @return attacks the unit started between its arrival and its last sample in the engagement, or its death
     */
    int getAttacksInEngagement() {
        return attacksAtExit - attacksAtArrival;
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
