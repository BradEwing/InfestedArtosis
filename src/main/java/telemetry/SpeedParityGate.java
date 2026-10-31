package telemetry;

import lombok.Value;

/**
 * What the speed parity gate read for one ground squad on one frame.
 */
@Value
public class SpeedParityGate {
    /** The squad. */
    String squadId;
    /** SPEED_PARITY, LING_UPGRADE or NONE. */
    String term;
    /** True when the gate switch let the term change the decision. */
    boolean applied;
    /** Our ling speed state: NONE, METABOLIC or ADRENAL. */
    String ourSpeed;
    /** The enemy's Metabolic Boost evidence: NONE, PLAUSIBLE or SEEN. */
    String enemySpeed;
    /** The enemy's earliest Metabolic Boost finish. */
    int enemyBound;
    /** What the enemy's earliest Metabolic Boost finish rests on. */
    String enemyAnchor;
    /** The squad's supply, in the unit the move out threshold is measured in. */
    int supply;
    /** The move out threshold the squad was held to. */
    int threshold;
    /** The move out threshold without the speed parity terms. */
    int baseThreshold;
    /** Zerglings we have alive. */
    int zerglings;
}
