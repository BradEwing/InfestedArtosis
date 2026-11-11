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
 * The damage dealt is read the same way, from the unit's own tally of the damage its weapon fire was priced at, which
 * does not depend on the attack count. The attack cycle columns are read the same way from the unit's own
 * unit.managed.AttackCycles, as their change between arrival and exit; they stay 0 unless combat telemetry is on.
 *
 * <p>The standoff episode and joiner flag are read at arrival: the standoff episode the unit stood in at
 * STANDOFF_ENTER or joined afterwards, -1 for none, and whether it joined after STANDOFF_ENTER. Units of one episode
 * that are not joiners are one launch cohort, which squad_at_arrival cannot name since a merge renames the squad.
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
    private final int damageAtArrival;
    private final AttackTally tallyAtArrival;
    private final StandoffCohort cohortAtArrival;
    private AttackTally tallyAtExit;

    private int exitFrame;
    private int hitPointsAtExit;
    private int attacksAtExit;
    private int damageAtExit;
    private int firstAttackFrame = -1;
    private boolean died;
    private int deathFrame = -1;
    private int deathX = -1;
    private int deathY = -1;

    EngagementUnit(int unitId, UnitType unitType, int arrivalFrame, int hitPoints, String roleAtArrival,
                   String squadAtArrival, AttackTally attacks) {
        this(unitId, unitType, arrivalFrame, hitPoints, roleAtArrival, squadAtArrival, attacks, StandoffCohort.NONE);
    }

    EngagementUnit(int unitId, UnitType unitType, int arrivalFrame, int hitPoints, String roleAtArrival,
                   String squadAtArrival, AttackTally attacks, StandoffCohort cohortAtArrival) {
        this.cohortAtArrival = cohortAtArrival;
        this.unitId = unitId;
        this.unitType = unitType;
        this.arrivalFrame = arrivalFrame;
        this.hitPointsAtArrival = hitPoints;
        this.roleAtArrival = roleAtArrival;
        this.squadAtArrival = squadAtArrival;
        this.attacksAtArrival = attacks.getAttacksStarted();
        this.damageAtArrival = attacks.getDamageDealt();
        this.exitFrame = arrivalFrame;
        this.hitPointsAtExit = hitPoints;
        this.attacksAtExit = attacks.getAttacksStarted();
        this.damageAtExit = attacks.getDamageDealt();
        this.tallyAtArrival = attacks;
        this.tallyAtExit = attacks;
    }

    /**
     * @param attacks the unit's attack counters as of this sample
     */
    void observe(int frame, int hitPoints, AttackTally attacks) {
        this.exitFrame = frame;
        this.hitPointsAtExit = hitPoints;
        observeAttacks(attacks);
    }

    /**
     * @param attacks the unit's attack counters as of this reading
     */
    void observeAttacks(AttackTally attacks) {
        this.attacksAtExit = attacks.getAttacksStarted();
        this.damageAtExit = attacks.getDamageDealt();
        this.tallyAtExit = attacks;
        if (firstAttackFrame < 0 && attacksAtExit > attacksAtArrival) {
            firstAttackFrame = attacks.getLastAttackStartFrame();
        }
    }

    /**
     * @return attacks the unit started between its arrival and its last sample in the engagement, or its death
     */
    int getAttacksInEngagement() {
        return attacksAtExit - attacksAtArrival;
    }

    /**
     * @return damage the unit dealt between its arrival and its last sample in the engagement, or its death
     */
    int getDamageInEngagement() {
        return damageAtExit - damageAtArrival;
    }

    /**
     * @param column a cycle column
     * @return how much its count rose between the unit's arrival and its last sample in the engagement, or its death
     */
    int cycleCountInEngagement(CycleColumn column) {
        return tallyAtExit.cycleCount(column) - tallyAtArrival.cycleCount(column);
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
