package telemetry;

import bwapi.Unit;
import unit.managed.ManagedUnit;
import util.TargetScorer;

/**
 * Static dispatch point for fight target changes. With no sink registered, every method is a no-op.
 */
public final class TargetChoices {

    private static TargetChoiceSink sink;

    private TargetChoices() {
    }

    public static void register(TargetChoiceSink targetChoiceSink) {
        sink = targetChoiceSink;
    }

    public static void clear() {
        sink = null;
    }

    /**
     * Forwards a selection to the sink only when it replaces the attacker's current target, so a
     * fighter that keeps its target every frame writes nothing.
     *
     * @param scoutCapped true if the scout chase cap removed a scout from the attacker's candidates
     */
    public static void chosen(ManagedUnit attacker, Unit previousTarget, TargetScorer.Selection selection,
                              boolean scoutCapped) {
        chosen(attacker, previousTarget, false, selection, scoutCapped);
    }

    /**
     * Forwards a selection to the sink when it replaces the attacker's current target, or when it keeps the target
     * but switches between attacking it and attack-moving to it, so every entry into and exit from overflow is
     * written.
     *
     * @param previousAttackMove true if the attacker was attack-moving to its current target
     * @param scoutCapped true if the scout chase cap removed a scout from the attacker's candidates
     */
    public static void chosen(ManagedUnit attacker, Unit previousTarget, boolean previousAttackMove,
                              TargetScorer.Selection selection, boolean scoutCapped) {
        TargetChoiceSink current = sink;
        if (current == null) {
            return;
        }
        if (previousTarget != null && !isChange(previousTarget.getID(), previousAttackMove,
                selection.getTarget().getID(), selection.isAttackMove())) {
            return;
        }
        current.onTargetChosen(attacker, previousTarget, selection, scoutCapped);
    }

    /**
     * Whether a pick differs from what the attacker held: another target, or the same target switched between a
     * direct attack and an attack-move.
     */
    static boolean isChange(int previousTargetId, boolean previousAttackMove, int targetId, boolean attackMove) {
        return previousTargetId != targetId || previousAttackMove != attackMove;
    }
}
