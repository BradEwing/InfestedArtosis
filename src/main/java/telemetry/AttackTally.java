package telemetry;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

/**
 * A unit's running attack counters as of one reading, each counted since the unit was first managed.
 */
@Value
class AttackTally {

    /**
     * Attacks the unit has started.
     */
    int attacksStarted;

    /**
     * Frame of the last of those attacks, -1 when none.
     */
    int lastAttackStartFrame;

    /**
     * Damage the unit's weapon fire was priced at.
     */
    int damageDealt;

    /**
     * Counts of every {@link CycleColumn}, indexed by ordinal.
     */
    @Getter(AccessLevel.NONE)
    int[] cycleCounts;

    /**
     * A tally with every cycle count 0.
     */
    static AttackTally of(int attacksStarted, int lastAttackStartFrame, int damageDealt) {
        return new AttackTally(attacksStarted, lastAttackStartFrame, damageDealt, new int[CycleColumn.values().length]);
    }

    /**
     * @param column a cycle column
     * @return its count
     */
    int cycleCount(CycleColumn column) {
        return cycleCounts[column.ordinal()];
    }
}
