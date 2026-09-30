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
     * An air squad started a harass. telemetry_harass.csv holds the ENTER row with the target base or exposed group.
     */
    HARASS_ENTER,

    /**
     * An air squad ended its harass and retreats. telemetry_harass.csv holds the EXIT row with the exit_reason.
     */
    HARASS_EXIT,

    /**
     * An air squad that left a harass recently held on a blind sim ADVANCE instead of marching on it.
     */
    HARASS_HOLD,

    /**
     * An air squad still under the retreat lock its harass exit armed acted on a sim ENGAGE measured against a real
     * enemy, which broke the lock.
     */
    HARASS_EXIT_ENGAGE,

    /**
     * A containing squad collapsed on the enemies inside its arc's sector: it left the arc for FIGHT under a fight
     * lock, every member fighting when it was under fire, else its flanks attack-moving past the enemy centroid while
     * the centre fights.
     */
    CONTAIN_COLLAPSE,

    /**
     * The wrap of a collapse ended, skipped under fire, every flank having arrived or the wrap having run out its
     * frames, and every member of the squad fights.
     */
    CONTAIN_COLLAPSE_COMMIT,

    /**
     * A squad held in RETREAT by the lock a contain's attrition exit armed read an ENGAGE at or above the strong
     * engage threshold, and the lock was dropped so the verdict could act.
     */
    RETREAT_LOCK_BROKEN,

    /**
     * A melee squad took a swarm lock and fights under one of our active Dark Swarms.
     */
    SWARM_COMMIT,

    /**
     * A melee squad holding a swarm lock kept fighting under its swarm, whatever the sim, a retreat lock or a
     * containment arc would have asked. On a SWARM_ACTIVE sample row it names a squad the lock has not taken.
     */
    SWARM_ACTIVE,

    /**
     * A melee squad dropped its swarm lock: the swarm fell below the sim horizon or was removed, a base came under
     * attack, a member stood in a Psionic Storm, the squad stopped being melee, or the swarm-priced sim read RETREAT.
     * The row's swarm_release_reason names which.
     */
    SWARM_EXPIRED,

    /**
     * No branch recorded a decision for this row.
     */
    NONE,

    /**
     * A rallying air squad flying to, or joining, an active air squad instead of waiting for its move out
     * threshold.
     */
    AIR_REINFORCE,

    /**
     * A contain timed out again after being re-entered in a row against an enemy defending with static defence
     * only, and every containing squad was committed to FIGHT under a fight lock instead of retreating, with arcs
     * barred for the hold window so the combat sim decides the attack.
     */
    CONTAIN_ESCALATE,

    /**
     * A contain against an enemy with army outside its static defence timed out as a stalemate: it had been
     * re-entered in a row after timeouts, its break was out of reach even at the supply cap, or a stalemate was
     * already detected. The squad retreats and arcs are barred for the stalemate hold window, so the combat sim
     * decides what the army does instead of the next contain.
     */
    CONTAIN_STALEMATE,

    /**
     * A detected contain stalemate met a maxed supply and committed the ground army: every ground squad but those
     * running by or harassing leaves its arc and fights toward the enemy, past combat sim retreats, until the army
     * falls below half the supply it committed with or no enemy target is known. Written as a row of its own for
     * every ground squad on the frame the commit starts.
     */
    STALEMATE_COMMIT,

    /**
     * A stalemate commit released: the ground army fell below half the supply it committed with, or no enemy
     * target is known. Written as a row of its own for every ground squad on the frame it releases.
     */
    STALEMATE_COMMIT_RELEASE,

    /**
     * A threat to one of our bases paused a running stalemate commit: the squads follow the normal rules until it
     * clears. Written as a row of its own for every ground squad on the frame the pause starts.
     */
    STALEMATE_COMMIT_PAUSE,

    /**
     * The base threat that paused a stalemate commit cleared and the commit resumed with its committed supply.
     * Written as a row of its own for every ground squad on the frame it resumes.
     */
    STALEMATE_COMMIT_RESUME,

    /**
     * A retreating ground squad whose last retreat plan found no path home clear of the enemy turned to fight, dropping
     * its retreat lock, once ENGAGE had held over a fight hysteresis window. It then stays in FIGHT for one more.
     */
    CORNERED_ENGAGE,

    /**
     * A retreating ground squad whose last retreat plan found its home contested turned to defend it instead of
     * staging at the edge of the threats on it, dropping its retreat lock, once ENGAGE or a measured read at or above
     * 0.9 of the engage threshold had held over a fight hysteresis window. It then stays in FIGHT for one more.
     */
    HOME_CONTESTED_DEFEND
}
