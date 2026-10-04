package telemetry;

/**
 * Why a squad's hold off a Bunker it retreated from ended, written on the BUNKER_HOLD_END row of the hold run.
 *
 * <p>The row is written on the first sweep after the last BUNKER_MEMORY_HOLD or BLIND_ADVANCE_HOLD row of a run. Any
 * other row carries NONE.
 */
public enum BunkerHoldRelease {
    /**
     * The squad's own composition grew past the one recorded at the retreat.
     */
    GROWTH,

    /**
     * Reinforcements that joined through merges added more than the allowed share of the supply that retreated.
     */
    MERGE_GROWTH,

    /**
     * The hold ran past its frame cap since the retreat.
     */
    TIME_CAP,

    /**
     * Every Bunker the squad held off stopped being a living observed Bunker.
     */
    BUNKER_DIED,

    /**
     * The squad left the fight squads, merged into a neighbour or emptied.
     */
    SQUAD_GONE,

    /**
     * The hold ended with the memory intact: the verdict changed, or the squad came inside a Bunker's priced reach.
     */
    OTHER,

    /**
     * The row ends no hold.
     */
    NONE
}
