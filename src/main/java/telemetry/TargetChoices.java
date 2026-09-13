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
     */
    public static void chosen(ManagedUnit attacker, Unit previousTarget, TargetScorer.Selection selection) {
        TargetChoiceSink current = sink;
        if (current == null) {
            return;
        }
        if (previousTarget != null && previousTarget.getID() == selection.getTarget().getID()) {
            return;
        }
        current.onTargetChosen(attacker, previousTarget, selection);
    }
}
