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

    /**
     * Attack cycles counted, see unit.managed.AttackCycles.
     */
    int attackCycles;

    /**
     * Frames between the two attack starts of every counted cycle, summed.
     */
    int cycleFrames;

    /**
     * Frames with the weapon cooling down inside the reach of an enemy that can attack the unit.
     */
    int cooldownFramesInThreat;

    /**
     * Hit points plus shields lost with the weapon cooling down.
     */
    int cooldownDamageTaken;

    /**
     * Frames of counted cycles with the weapon ready and no attack started.
     */
    int readyFrames;
}
