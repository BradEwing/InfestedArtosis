package unit.squad;

/**
 * Roles a Squad can hold.
 *
 * <p>Each status carries a merge precedence used when two squads combine into one.
 *
 * <p>Highest to lowest: FIGHT, CONTAIN, RETREAT, RALLY, DEFENSE.
 *
 * <ul>
 *     <li>FIGHT wins outright</li>
 *     <li>CONTAIN outranks RETREAT and RALLY because a containing squad has already cleared the moveout threshold and
 *     committed to an arc forward of the rally point</li>
 *     <li>RETREAT outranks RALLY so a squad disengaging under threat is not downgraded</li>
 *     <li>DEFENSE sits last. Defense squads are keyed by base and currently never enter the fight squad merge</li>
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
     * <p>A null status loses to any real status, which lets a squad that has not been assigned a status adopt
     * the state of the squads it is built from.
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
