package telemetry;

import bwapi.Unit;
import unit.managed.ManagedUnit;
import util.TargetScorer;

/**
 * Receives fight target changes. Implementations are registered with {@link TargetChoices} and must
 * never throw: they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface TargetChoiceSink {

    /**
     * A fighter was given a fight target different from the one it held.
     *
     * @param attacker the fighter, whose fight target has not been replaced yet
     * @param previousTarget the target it held, or null if it held none
     * @param selection the target TargetScorer chose, with its tier and candidate count
     * @param scoutCapped true if the scout chase cap removed a scout from the attacker's candidates
     */
    void onTargetChosen(ManagedUnit attacker, Unit previousTarget, TargetScorer.Selection selection,
                        boolean scoutCapped);
}
