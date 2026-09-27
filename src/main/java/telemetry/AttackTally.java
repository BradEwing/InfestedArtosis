package telemetry;

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
}
