package telemetry;

import bwapi.Position;
import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.ContainmentCollapse;
import unit.squad.DefenseSim;
import unit.squad.RunbyState;
import unit.squad.Squad;

import java.util.List;

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
     * A member of a containing squad was hit by an enemy that outranges it and the squad kept an arc out of that
     * enemy's reach, whether or not any member had to move to hold it.
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
     * A containing squad was evaluated, with whether a member was hit this frame by something it cannot answer.
     */
    void onOutrangedHitEvaluated(Squad squad, boolean outrangedHit);

    /**
     * A containing squad tested whether to collapse on the armed enemies inside its arc's sector. Called only when
     * at least one stands there.
     *
     * @param outcome COLLAPSE, or the first condition that failed
     * @param enemiesInSector armed enemies inside the sector
     * @param ratio the squad's strength over exactly the enemies in the sector, -1 when fewer than the minimum stood
     *     there and no sim ran
     * @param flanks members that flank in a collapse of this squad
     * @param staticClear true when the enemy centroid is clear of static defence reach
     */
    void onContainmentCollapseEvaluated(Squad squad, ContainmentCollapse.Outcome outcome, int enemiesInSector,
                                        double ratio, int flanks, boolean staticClear);

    /**
     * A squad was offered a containment arc, with the distance from its center to the nearest arc point. A squad
     * farther than the arrival distance stays in transit and keeps simulating.
     */
    void onContainArcMeasured(Squad squad, int distance);

    /**
     * A fight squad's strength was compared against its move out threshold, in the threshold's units: air
     * combat units for an air squad, BWAPI half-supply for a ground squad.
     */
    void onMoveOutEvaluated(Squad squad, int moveOutThreshold, int squadStrength);

    /**
     * Worker defence at a base pulled gatherers, abandoned its defence, or released its defenders.
     *
     * <p>sim is the full commitment simulation behind a PULL or ABANDON, and null when none ran.
     *
     * @param pulled gatherers the defence took on, in pull order
     * @param released defenders the defence let go, still holding the roles they had in the squad
     */
    void onDefenseEvaluated(Squad squad, DefenseEvent event, int candidates, List<ManagedUnit> pulled,
                            List<ManagedUnit> released, DefenseSim sim);
}
