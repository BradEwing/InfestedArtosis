package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import unit.squad.CombatSimulator;
import unit.squad.DefenseSim;
import unit.squad.RunbyState;
import unit.squad.Squad;

/**
 * Receives the inputs SquadManager used to pick a squad status. Implementations must never throw:
 * they run inside the per frame squad loop, where an escaped exception kills the JVM.
 */
public interface SquadDecisionSink {

    /**
     * The combat sim verdict for a squad, together with the lock state read just before it.
     *
     * <p>The lock booleans are the state the verdict was judged against. The lock columns on the
     * row are read from the squad when the row is built, so a row emitted on a frame with no
     * verdict still reports them.
     */
    void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked, boolean fightLocked);

    /**
     * A lock kept the squad on its current status after the simulator asked for the other one.
     */
    void onLockSuppressed(Squad squad, SquadLock lock);

    /**
     * The branch of SquadManager that decided this squad's status on this frame.
     *
     * <p>Called by every branch that sets or holds a status. The last call of the frame wins, so a
     * branch that re-decides a status set earlier in the same frame is the one the row names.
     */
    void onPathTaken(Squad squad, DecisionPath path);

    /**
     * A split that would have fragmented the squad below the move out floor.
     *
     * <p>Fires for every declined split, whether the squad is committed or not. The
     * {@code committed} column on the resulting row records which case applies.
     */
    void onSplitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength);

    /**
     * A squad was sent to the rally point, with the branch that put it there.
     */
    void onRallied(Squad squad, RallyReason reason);

    /**
     * A squad that was rallying is no longer rallying, with the term that released it.
     */
    void onRallyReleased(Squad squad, RallyRelease release);

    /**
     * The containment verdict for a squad that was eligible to enter an arc this frame.
     *
     * <p>canBreakContainment is only meaningful when shouldContain is true.
     */
    void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment, boolean entered);

    /**
     * A runby squad started a phase without changing its status, so the status sweep would not see it.
     *
     * @param squad the runby squad
     * @param from the phase it left
     * @param to the phase it started
     * @param path the branch that started it
     */
    void onRunbyPhaseStarted(Squad squad, RunbyState.Phase from, RunbyState.Phase to, DecisionPath path);

    /**
     * A containing squad moved its arc back out of the reach of an enemy that outranges it.
     *
     * @param from midpoint of the arc the squad held
     * @param to midpoint of the arc it holds now
     * @param enemyType type of the outranging enemy with the longest reach
     * @param membersMoved members whose contain position changed
     */
    void onContainmentPushedBack(Squad squad, Position from, Position to, UnitType enemyType, int membersMoved);

    /**
     * A containing squad left its arc, with the supply it lost over the episode, in BWAPI half-supply units.
     */
    void onContainmentEnded(Squad squad, int supplyLost);

    /**
     * Worker defence at a base pulled gatherers, abandoned its defence, or released its defenders.
     *
     * <p>sim is the full commitment simulation behind a PULL or ABANDON, and null when none ran.
     */
    void onDefenseEvaluated(Squad squad, DefenseEvent event, int candidates, int pulled, int released,
                            DefenseSim sim);
}
