package unit.squad;

/**
 * Roles a Squad can hold.
 *
 * <p>Each status carries a merge precedence used when two squads combine into one. Precedence is a total order, so the
 * surviving status is a function of the statuses alone and never of the order the merging squads are iterated in.
 *
 * <p>Highest to lowest: FIGHT, CONTAIN, RETREAT, RALLY, DEFENSE.
 *
 * <ul>
 *     <li>FIGHT wins outright because contact with the enemy already overrides every other status elsewhere: a
 *     containing squad drops to FIGHT the moment enemies are near, and a squad below its moveout threshold is still
 *     simulated rather than rallied while it holds FIGHT. A merge only makes a squad stronger, so absorbing units
 *     must never pull an engaged squad out of its engagement.</li>
 *     <li>CONTAIN outranks RETREAT and RALLY because a containing squad has already cleared the moveout threshold and
 *     committed to an arc forward of the rally point, while neither of those has committed forward.</li>
 *     <li>RETREAT outranks RALLY so a squad disengaging under threat is not downgraded to the passive gathering
 *     state.</li>
 *     <li>DEFENSE sits last. Defense squads are keyed by base and never enter the fight squad merge, so it is ordered
 *     here only to keep the comparison total.</li>
 * </ul>
 */
public enum SquadStatus {
    FIGHT(4),
    DEFENSE(0),
    RALLY(1),
    RETREAT(2),
    CONTAIN(3);

    private final int mergePrecedence;

    SquadStatus(int mergePrecedence) {
        this.mergePrecedence = mergePrecedence;
    }

    /**
     * Returns the status that survives when two squads holding these statuses merge.
     *
     * <p>A null status loses to any real status, which lets a squad that has not been assigned a status yet fold in
     * the state of the squads it is built from. Two nulls yield null.
     *
     * @param first status of one merging squad
     * @param second status of the other merging squad
     * @return the higher precedence of the two
     */
    public static SquadStatus dominant(SquadStatus first, SquadStatus second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.mergePrecedence >= second.mergePrecedence ? first : second;
    }
}
