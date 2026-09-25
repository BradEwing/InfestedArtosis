package telemetry;

/**
 * The branch of SquadManager that decided a squad's status on the frame a row describes.
 *
 * <p>On a STATUS_CHANGE row this names what produced the new status. On a LOCK_SUPPRESSED row it
 * names the request the lock refused, which is the branch that would have set the status had the
 * lock not held; the lock branches record {@link #RETREAT_LOCK} or {@link #FIGHT_LOCK} only after
 * that row is written, so the held status on any later row is still attributed to the lock.
 */
public enum DecisionPath {

    /**
     * The squad has no detected enemy unit anywhere and at least one remembered enemy building, so
     * the fight target chain falls through to the march on that building.
     */
    NO_VISION_MARCH,

    /**
     * A ground squad of Lurkers only, which fights where it stands.
     */
    LURKER_ONLY,

    /**
     * The combat sim returned ADVANCE and the blind advance rule did not hold it.
     */
    SIM_ADVANCE,

    /**
     * The combat sim returned ENGAGE.
     */
    SIM_ENGAGE,

    /**
     * The combat sim returned RETREAT and the squad did not enter a containment arc instead.
     */
    SIM_RETREAT,

    /**
     * The retreat hysteresis lock kept the squad retreating.
     */
    RETREAT_LOCK,

    /**
     * The fight hysteresis lock kept the squad fighting.
     */
    FIGHT_LOCK,

    /**
     * A unit of the squad is standing in a psionic storm.
     */
    STORM_RETREAT,

    /**
     * The squad took a containment arc.
     */
    CONTAIN_ENTER,

    /**
     * Containment broke army wide and every containing squad was committed.
     */
    CONTAIN_BREAK,

    /**
     * A containing squad left its arc without the army breaking.
     */
    CONTAIN_RETREAT,

    /**
     * A containing squad ran by into the enemy base.
     */
    RUNBY_ENTER,

    /**
     * A runby squad started a phase: HARASS after PENETRATE, or PENETRATE again at a new target base.
     */
    RUNBY_PHASE,

    /**
     * A runby squad met overwhelming enemy strength inside its abort window and retreated.
     */
    RUNBY_ABORT,

    /**
     * A runby squad ran out of targets at its base and moved on to the next known enemy base.
     */
    RUNBY_RETARGET,

    /**
     * A runby squad ran out of targets with no other enemy base known, and retreated.
     */
    RUNBY_EXIT_NO_TARGETS,

    /**
     * A containing squad left its arc because it was losing supply while killing little.
     */
    CONTAIN_ATTRITION,

    /**
     * A containing squad left its arc because no arc point on the choke stayed out of reach of an enemy that
     * outranges it.
     */
    CONTAIN_OUTRANGED,

    /**
     * A containing squad was hit by an enemy that outranges it, recomputed its arc out of that enemy's reach and kept
     * containing. The arc may be unchanged when it already stood out of reach.
     */
    CONTAIN_PUSHBACK,

    /**
     * The squad was sent to rally, at the rally point or, for JOIN_CONTAIN, on an active containment arc. The
     * rally_reason column names which branch.
     */
    RALLY,

    /**
     * The squad was created this frame by a merge and holds the status folded from its sources.
     */
    MERGE_INHERIT,

    /**
     * The squad was created this frame by a split and holds the status it inherited from its
     * parent.
     */
    SPLIT_INHERIT,

    /**
     * A containing squad collapsed on the enemies inside its arc's sector: it left the arc for FIGHT under a fight
     * lock, its flanks wrapping past the enemy centroid while the centre holds.
     */
    CONTAIN_COLLAPSE,

    /**
     * The wrap of a collapse ended, every flank having arrived or the wrap having run out its frames, and the centre
     * of the squad committed to the fight.
     */
    CONTAIN_COLLAPSE_COMMIT,

    /**
     * A squad held in RETREAT by the lock a contain's attrition exit armed read an ENGAGE at or above the strong
     * engage threshold, and the lock was dropped so the verdict could act.
     */
    RETREAT_LOCK_BROKEN,

    /**
     * No branch recorded a decision for this row.
     */
    NONE
}
