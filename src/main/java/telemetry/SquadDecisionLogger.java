package telemetry;

import bwapi.Game;
import bwapi.Position;
import bwapi.UnitType;
import info.GameState;
import unit.squad.CombatSimulator;
import unit.squad.DefenseSim;
import unit.squad.RunbyState;
import unit.squad.Squad;
import unit.squad.SquadManager;
import unit.squad.SquadStatus;
import unit.squad.horizon.HorizonCombatSimulator;
import util.Arc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records squad status changes and the decisions behind them.
 *
 * <p>Writes three event types. STATUS_CHANGE is emitted when a fight squad's {@link SquadStatus}
 * differs from the previous frame sweep. LOCK_SUPPRESSED is emitted when a hysteresis lock holds a
 * squad on its current status after the simulator asks for the other one. SPLIT_SUPPRESSED is
 * emitted when splitSquads keeps a squad together because a split would drop a side below the move
 * out floor; suppressed_by carries MOVE_OUT_FLOOR on those rows. SQUAD_DISBANDED is emitted when a
 * squad leaves the fight squads, whether it merged, emptied or disbanded for want of targets, so a
 * RALLY episode that never resolves is still bounded. PHASE_CHANGE is emitted when a RUNBY squad starts a
 * phase, which changes no status and so would never reach a STATUS_CHANGE row; runby_phase_old and
 * runby_phase carry the phase it left and the one it started.
 *
 * <p>runby_phase names the phase a RUNBY squad is in on every row, and NONE for any other status.
 * runby_phase_old is NONE on every row except PHASE_CHANGE.
 *
 * <p>DEFENSE_PULL, DEFENSE_ABANDON and DEFENSE_RELEASE rows describe worker defence squads, with
 * squad_type DEFENSE. They carry the candidate, pulled and released worker counts and the full
 * commitment simulation; sim_result is ENGAGE when that simulation wins, RETREAT when it loses and
 * NONE when none ran. Fight squad rows leave the defense columns at -1.
 *
 * <p>Rows for a squad holding a containment arc carry the arc's center and its points as x:y pairs joined by
 * semicolons; every other row carries -1 and NONE there.
 *
 * <p>CONTAIN_PUSHBACK is emitted when a containing squad moves its arc back out of reach of an enemy that
 * outranges it, with the old and new arc midpoints, the enemy type and the members moved. The row that closes a
 * containment episode carries the supply lost over it.
 *
 * <p>Every row names the branch that decided the status it reports in decision_path. On a
 * LOCK_SUPPRESSED row that is the request the lock refused, so the suppression episodes a lock
 * produced are separable by the branch that asked for them.
 *
 * <p>LOCK_SUPPRESSED rows are deduplicated per suppression episode, keyed on the lock, its expiry
 * frame, the overridden verdict, and the branch that asked for it.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class SquadDecisionLogger implements SquadDecisionSink {

    static final String FILE = "telemetry_squad_decisions.csv";

    static final String HEADER = "game_id,frame,squad_id,squad_type,event,old_status,new_status,sim_result,"
            + "suppressed_by,our_supply_real,squad_size,enemy_supply_believed_real,enemy_scouted,sim_our_strength,"
            + "sim_enemy_strength,sim_ratio,sim_engage_threshold,retreat_locked,fight_locked,"
            + "retreat_lock_until_frame,fight_lock_until_frame,committed,commit_frame,should_contain,"
            + "can_break_containment,containment_entered,centroid_x,centroid_y,ground_distance_to_base,"
            + "rally_reason,rally_release,defense_candidates,workers_pulled,workers_released,"
            + "defense_sim_defenders,defense_sim_enemies,defense_sim_defender_survivors,"
            + "defense_sim_enemy_survivors,defense_win_threshold,arc_center_x,arc_center_y,arc_points,"
            + "decision_path,sim_enemy_composition,sim_enemy_unscored_supply,runby_phase_old,runby_phase,"
            + "pushback_from_x,pushback_from_y,pushback_to_x,pushback_to_y,pushback_enemy_type,"
            + "pushback_members_moved,contain_supply_lost";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final String EVENT_STATUS_CHANGE = "STATUS_CHANGE";
    private static final String EVENT_LOCK_SUPPRESSED = "LOCK_SUPPRESSED";
    private static final String EVENT_SPLIT_SUPPRESSED = "SPLIT_SUPPRESSED";
    private static final String EVENT_SQUAD_DISBANDED = "SQUAD_DISBANDED";
    static final String EVENT_PHASE_CHANGE = "PHASE_CHANGE";
    private static final String EVENT_CONTAIN_PUSHBACK = "CONTAIN_PUSHBACK";
    private static final String EVENT_DEFENSE_PREFIX = "DEFENSE_";
    private static final String SQUAD_TYPE_DEFENSE = "DEFENSE";
    private static final int SQUAD_TYPE_CELL = 3;
    private static final String MOVE_OUT_FLOOR = "MOVE_OUT_FLOOR";
    private static final String NONE = "NONE";

    private final Game game;
    private final GameState gameState;
    private final SquadManager squadManager;
    private final String gameId;
    private final TelemetryWriter writer;

    private final Map<String, SquadStatus> lastStatus = new HashMap<>();
    private final Map<String, SquadDecision> decisions = new HashMap<>();
    private final Map<String, String> lastSuppression = new HashMap<>();
    private final Map<String, Squad> lastSquad = new HashMap<>();
    private final Map<String, RallyReason> rallyReason = new HashMap<>();

    private boolean disabled;

    public SquadDecisionLogger(Game game, GameState gameState, SquadManager squadManager, String gameId) {
        this.game = game;
        this.gameState = gameState;
        this.squadManager = squadManager;
        this.gameId = gameId;
        this.writer = new TelemetryWriter(FILE, HEADER);
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            int frame = game.getFrameCount();
            sweepStatuses(frame);
            if (frame % FLUSH_INTERVAL_FRAMES == 0) {
                writer.flush();
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onEnd() {
        if (disabled) {
            return;
        }

        try {
            sweepStatuses(game.getFrameCount());
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onSimEvaluated(Squad squad, CombatSimulator.CombatResult result, boolean retreatLocked,
                               boolean fightLocked) {
        if (disabled) {
            return;
        }

        try {
            SquadDecision decision = decisionFor(squad);
            decision.setResult(result);
            readSnapshot(squad, decision);
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onLockSuppressed(Squad squad, SquadLock lock) {
        if (disabled) {
            return;
        }

        try {
            SquadDecision decision = decisions.get(squad.getId());
            if (decision == null || !overridesVerdict(squad.getStatus(), lock, decision.getResult())) {
                return;
            }

            String episode = lock.name() + "@" + lockUntilFrame(squad, lock) + ":" + decision.getResult()
                    + ":" + decision.getDecisionPath();
            if (episode.equals(lastSuppression.get(squad.getId()))) {
                return;
            }
            lastSuppression.put(squad.getId(), episode);
            writer.append(row(squad, game.getFrameCount(), EVENT_LOCK_SUPPRESSED, squad.getStatus(),
                    squad.getStatus(), decision, lock.name()));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onPathTaken(Squad squad, DecisionPath path) {
        if (disabled) {
            return;
        }

        try {
            decisionFor(squad).setDecisionPath(path);
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onRallied(Squad squad, RallyReason reason) {
        if (disabled) {
            return;
        }

        try {
            rallyReason.put(squad.getId(), reason);
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onRallyReleased(Squad squad, RallyRelease release) {
        if (disabled) {
            return;
        }

        try {
            decisionFor(squad).setRallyRelease(release);
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onContainmentEvaluated(Squad squad, boolean shouldContain, boolean canBreakContainment,
                                       boolean entered) {
        if (disabled) {
            return;
        }

        try {
            SquadDecision decision = decisionFor(squad);
            decision.setShouldContain(SquadDecision.tristate(shouldContain));
            decision.setCanBreakContainment(shouldContain
                    ? SquadDecision.tristate(canBreakContainment) : SquadDecision.NOT_EVALUATED);
            decision.setContainmentEntered(SquadDecision.tristate(entered));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onContainmentPushedBack(Squad squad, Position from, Position to, UnitType enemyType,
                                        int membersMoved) {
        if (disabled) {
            return;
        }

        try {
            SquadDecision context = new SquadDecision();
            context.setDecisionPath(DecisionPath.CONTAIN_PUSHBACK);
            context.setPushbackFrom(from);
            context.setPushbackTo(to);
            context.setPushbackEnemyType(enemyType);
            context.setPushbackMembersMoved(membersMoved);
            writer.append(row(squad, game.getFrameCount(), EVENT_CONTAIN_PUSHBACK, squad.getStatus(),
                    squad.getStatus(), context, NONE));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onContainmentEnded(Squad squad, int supplyLost) {
        if (disabled) {
            return;
        }

        try {
            decisionFor(squad).setContainSupplyLost(supplyLost);
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onSplitSuppressed(Squad squad, int moveOutThreshold, int squadStrength, int outlierStrength) {
        if (disabled) {
            return;
        }

        try {
            writer.append(row(squad, game.getFrameCount(), EVENT_SPLIT_SUPPRESSED, squad.getStatus(),
                    squad.getStatus(), decisions.get(squad.getId()), MOVE_OUT_FLOOR));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onRunbyPhaseStarted(Squad squad, RunbyState.Phase from, RunbyState.Phase to, DecisionPath path) {
        if (disabled) {
            return;
        }

        try {
            SquadDecision context = new SquadDecision();
            context.setDecisionPath(path);
            writer.append(row(squad, game.getFrameCount(), EVENT_PHASE_CHANGE, squad.getStatus(), squad.getStatus(),
                    context, NONE, runbyCells(from, to)));
        } catch (RuntimeException e) {
            disable();
        }
    }

    @Override
    public void onDefenseEvaluated(Squad squad, DefenseEvent event, int candidates, int pulled, int released,
                                   DefenseSim sim) {
        if (disabled) {
            return;
        }

        try {
            writer.append(defenseRow(squad, game.getFrameCount(), event, candidates, pulled, released, sim));
        } catch (RuntimeException e) {
            disable();
        }
    }

    /**
     * Disables the logger and clears its state after an error, so a telemetry failure cannot crash the game.
     */
    private void disable() {
        disabled = true;
        decisions.clear();
        lastStatus.clear();
        lastSuppression.clear();
        lastSquad.clear();
        rallyReason.clear();
        SquadDecisions.clear();
    }

    private SquadDecision decisionFor(Squad squad) {
        SquadDecision decision = decisions.get(squad.getId());
        if (decision == null) {
            decision = new SquadDecision();
            decisions.put(squad.getId(), decision);
        }
        return decision;
    }

    /**
     * Emits a row for every fight squad whose status changed since the last sweep, then drops
     * bookkeeping for squads that no longer exist. A squad's first appearance is reported as a
     * transition from NONE.
     */
    private void sweepStatuses(int frame) {
        Set<String> present = new HashSet<>();
        for (Squad squad : squadManager.fightSquads) {
            String id = squad.getId();
            present.add(id);
            lastSquad.put(id, squad);
            SquadStatus current = squad.getStatus();
            SquadStatus previous = lastStatus.get(id);
            if (current == previous) {
                continue;
            }
            lastStatus.put(id, current);
            writer.append(row(squad, frame, EVENT_STATUS_CHANGE, previous, current, decisions.get(id), NONE));
        }

        emitDisbands(frame, present);

        lastStatus.keySet().retainAll(present);
        lastSuppression.keySet().retainAll(present);
        lastSquad.keySet().retainAll(present);
        rallyReason.keySet().retainAll(present);
        decisions.clear();
    }

    /**
     * Emits the terminal row for every squad that was present at the last sweep and is gone now,
     * whichever way it left: merged into a neighbour, emptied by losses, or disbanded for want of
     * targets. Without it a squad that never changed status again simply stopped appearing, so an
     * episode that was open when it left was never closed and its dwell had no upper bound.
     *
     * <p>The squad object outlives its membership in fightSquads, so the row still carries its last
     * centroid and composition. Runs before the bookkeeping is dropped, so the rally reason the
     * episode opened with is still readable.
     */
    private void emitDisbands(int frame, Set<String> present) {
        for (Map.Entry<String, Squad> entry : lastSquad.entrySet()) {
            if (present.contains(entry.getKey())) {
                continue;
            }
            SquadStatus last = lastStatus.get(entry.getKey());
            SquadDecision context = new SquadDecision();
            context.setRallyRelease(releaseOnDisband(last));
            writer.append(row(entry.getValue(), frame, EVENT_SQUAD_DISBANDED, last, null, context, NONE));
        }
    }

    /**
     * Returns the release a terminal row records. Only a squad that was rallying when it vanished
     * closes a RALLY episode; for any other status the column stays NONE, so counting DISBANDED
     * counts unresolved rallies and nothing else.
     */
    static RallyRelease releaseOnDisband(SquadStatus last) {
        return last == SquadStatus.RALLY ? RallyRelease.DISBANDED : RallyRelease.NONE;
    }

    private void readSnapshot(Squad squad, SquadDecision decision) {
        CombatSimulator simulator = squad.getCombatSimulator();
        if (!(simulator instanceof HorizonCombatSimulator)) {
            return;
        }

        HorizonCombatSimulator.DebugSnapshot snapshot =
                ((HorizonCombatSimulator) simulator).getLastSnapshots().get(squad.getId());
        if (snapshot == null) {
            return;
        }

        decision.setSimSampled(true);
        decision.setOurStrength(snapshot.getFriendlyTotal());
        decision.setEnemyStrength(snapshot.getEnemyTotal());
        decision.setRatio(snapshot.getOverallRatio());
        decision.setEngageThreshold(snapshot.getEngageThreshold());
        decision.setEnemySupplyBelieved(believedEnemySupply(snapshot));
        String composition = Csv.sanitize(HorizonCombatSimulator.enemyComposition(snapshot));
        decision.setEnemyComposition(composition.isEmpty() ? NONE : composition);
        decision.setEnemyUnscoredSupply(snapshot.getEnemyUnscoredSupply());
    }

    /**
     * Returns the total supply of enemy units the simulator counted, including units known only
     * from fog of war.
     */
    private int believedEnemySupply(HorizonCombatSimulator.DebugSnapshot snapshot) {
        int supply = 0;
        for (HorizonCombatSimulator.UnitDebugEntry entry : snapshot.getEnemyUnits()) {
            supply += entry.getType().supplyRequired();
        }
        return supply;
    }

    /**
     * Returns true if the lock held back a transition the simulator's verdict asked for.
     */
    static boolean overridesVerdict(SquadStatus status, SquadLock lock,
                                    CombatSimulator.CombatResult result) {
        if (lock == SquadLock.RETREAT) {
            return status == SquadStatus.RETREAT && result != null
                    && result != CombatSimulator.CombatResult.RETREAT;
        }
        return status == SquadStatus.FIGHT && result == CombatSimulator.CombatResult.RETREAT;
    }

    private static int lockUntilFrame(Squad squad, SquadLock lock) {
        return lock == SquadLock.RETREAT ? squad.getRetreatLockedUntilFrame() : squad.getFightLockedUntilFrame();
    }

    private static String squadType(Squad squad) {
        if (squad.isGroundSquad()) {
            return "GROUND";
        }
        if (squad.isAirSquad()) {
            return "AIR";
        }
        return "MIXED";
    }

    private String row(Squad squad, int frame, String event, SquadStatus from, SquadStatus to,
                       SquadDecision decision, String suppressedBy) {
        return row(squad, frame, event, from, to, decision, suppressedBy, runbyCells(null, currentRunbyPhase(squad)));
    }

    private String row(Squad squad, int frame, String event, SquadStatus from, SquadStatus to,
                       SquadDecision decision, String suppressedBy, List<String> runbyCells) {
        SquadDecision context = decision != null ? decision : new SquadDecision();
        List<String> fields = new ArrayList<>(identityCells(gameId, frame, squad, event, from, to, context,
                suppressedBy));
        fields.addAll(squadCells(squad, context,
                gameState.getScoutData().isEnemyBuildingLocationKnown(),
                groundDistanceToNearestBase(squad.getCenter()), frame));
        fields.addAll(rallyCells(rallyReason.getOrDefault(squad.getId(), RallyReason.NONE),
                context.getRallyRelease()));
        fields.addAll(defenseCells(SquadDecision.NOT_EVALUATED, SquadDecision.NOT_EVALUATED,
                SquadDecision.NOT_EVALUATED, null));
        fields.addAll(arcCells(squad));
        fields.addAll(pathCells(context));
        fields.addAll(enemySampleCells(context));
        fields.addAll(runbyCells);
        fields.addAll(containmentCells(context));
        return String.join(",", fields);
    }

    private String defenseRow(Squad squad, int frame, DefenseEvent event, int candidates, int pulled, int released,
                              DefenseSim sim) {
        SquadDecision context = new SquadDecision();
        if (sim != null && sim.isSimulated()) {
            context.setResult(sim.wins() ? CombatSimulator.CombatResult.ENGAGE : CombatSimulator.CombatResult.RETREAT);
        }
        List<String> fields = new ArrayList<>(defenseIdentityCells(gameId, frame, squad, event, context));
        fields.addAll(squadCells(squad, context,
                gameState.getScoutData().isEnemyBuildingLocationKnown(),
                groundDistanceToNearestBase(squad.getCenter()), frame));
        fields.addAll(rallyCells(RallyReason.NONE, RallyRelease.NONE));
        fields.addAll(defenseCells(candidates, pulled, released, sim));
        fields.addAll(arcCells(squad));
        fields.addAll(pathCells(context));
        fields.addAll(enemySampleCells(context));
        fields.addAll(runbyCells(null, null));
        fields.addAll(containmentCells(context));
        return String.join(",", fields);
    }

    /**
     * Builds the runby_phase_old and runby_phase cells.
     *
     * @param from phase a PHASE_CHANGE row left, or null on any other row
     * @param to phase the squad is in, or null when it is not running by
     * @return the two cells, NONE where a phase is null
     */
    static List<String> runbyCells(RunbyState.Phase from, RunbyState.Phase to) {
        List<String> fields = new ArrayList<>();
        fields.add(Csv.name(from));
        fields.add(Csv.name(to));
        return fields;
    }

    private static RunbyState.Phase currentRunbyPhase(Squad squad) {
        RunbyState state = squad.getRunbyState();
        return squad.getStatus() == SquadStatus.RUNBY && state != null ? state.getPhase() : null;
    }

    /**
     * Builds the two cells that describe what the simulator sampled on the enemy side: the
     * composition it measured, as Type:count pairs joined by semicolons, and the supply inside that
     * composition it then priced at nothing.
     *
     * <p>Both come from the same enemy list that enemy_supply_believed_real is summed over, so the
     * supply implied by the composition is that column by construction, and the unscored supply is a
     * subset of it. Either is the not evaluated sentinel on a row whose decision never read a
     * simulator snapshot.
     *
     * @param context the decision the row is built from
     * @return the composition cell and the unscored supply cell
     */
    static List<String> enemySampleCells(SquadDecision context) {
        List<String> fields = new ArrayList<>();
        fields.add(context.getEnemyComposition());
        fields.add(context.getEnemyUnscoredSupply() < 0
                ? String.valueOf(SquadDecision.NOT_EVALUATED)
                : Csv.halfSupply(context.getEnemyUnscoredSupply()));
        return fields;
    }

    /**
     * Builds the containment episode cells: where a push back moved the arc from and to, the enemy that forced
     * it and how many members moved, then the supply lost over the episode in real supply.
     *
     * <p>The push back cells are filled only on a CONTAIN_PUSHBACK row, and the supply lost only on the row that
     * closes a containment episode. Every other row carries the not evaluated sentinels.
     *
     * @param context the decision the row is built from
     * @return the push back cells and the supply lost cell
     */
    static List<String> containmentCells(SquadDecision context) {
        List<String> fields = new ArrayList<>();
        Position from = context.getPushbackFrom();
        Position to = context.getPushbackTo();
        fields.add(String.valueOf(from != null ? from.getX() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(from != null ? from.getY() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(to != null ? to.getX() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(to != null ? to.getY() : SquadDecision.NOT_EVALUATED));
        fields.add(Csv.name(context.getPushbackEnemyType()));
        fields.add(String.valueOf(context.getPushbackMembersMoved()));
        fields.add(context.getContainSupplyLost() < 0
                ? String.valueOf(SquadDecision.NOT_EVALUATED)
                : Csv.halfSupply(context.getContainSupplyLost()));
        return fields;
    }

    /**
     * Builds the identity cells: game, frame, squad, event, the statuses before and after the
     * transition, the simulator verdict, and what suppressed the action (NONE if nothing did).
     */
    static List<String> identityCells(String gameId, int frame, Squad squad, String event, SquadStatus from,
                                      SquadStatus to, SquadDecision context, String suppressedBy) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(squad.getId());
        fields.add(squadType(squad));
        fields.add(event);
        fields.add(Csv.name(from));
        fields.add(Csv.name(to));
        fields.add(context.getResult() != null ? context.getResult().name() : NONE);
        fields.add(suppressedBy);
        return fields;
    }

    /**
     * Builds the identity cells of a worker defence row: squad_type DEFENSE, event DEFENSE_ followed by
     * the defence event, and no status transition.
     */
    static List<String> defenseIdentityCells(String gameId, int frame, Squad squad, DefenseEvent event,
                                             SquadDecision context) {
        List<String> fields = identityCells(gameId, frame, squad, EVENT_DEFENSE_PREFIX + event.name(), null, null,
                context, NONE);
        fields.set(SQUAD_TYPE_CELL, SQUAD_TYPE_DEFENSE);
        return fields;
    }

    /**
     * Builds the cells that measure the squad at decision time, from our_supply_real through
     * ground_distance_to_base. commit_frame is -1 when the squad is not committed, matching the
     * {@link SquadDecision#NOT_EVALUATED} sentinel used by the sim columns.
     *
     * <p>retreat_locked and fight_locked are read from the squad against the row's own frame, so a
     * row emitted on a frame where no simulation ran still reports the real lock state.
     *
     * @param squad squad the row describes
     * @param context decision accumulated for the squad this frame
     * @param enemyScouted whether an enemy building location is known
     * @param groundDistanceToBase ground path length to the closest base held
     * @param frame frame the row is emitted on
     * @return the squad measurement cells
     */
    static List<String> squadCells(Squad squad, SquadDecision context, boolean enemyScouted,
                                   int groundDistanceToBase, int frame) {
        Position center = squad.getCenter();
        List<String> fields = new ArrayList<>();
        fields.add(Csv.halfSupply(squad.getSupply()));
        fields.add(String.valueOf(squad.size()));
        fields.add(context.getEnemySupplyBelieved() < 0
                ? String.valueOf(SquadDecision.NOT_EVALUATED)
                : Csv.halfSupply(context.getEnemySupplyBelieved()));
        fields.add(String.valueOf(SquadDecision.tristate(enemyScouted)));
        fields.add(Csv.format(context.getOurStrength()));
        fields.add(Csv.format(context.getEnemyStrength()));
        fields.add(Csv.format(context.getRatio()));
        fields.add(Csv.format(context.getEngageThreshold()));
        fields.add(String.valueOf(SquadDecision.tristate(squad.isRetreatLocked(frame))));
        fields.add(String.valueOf(SquadDecision.tristate(squad.isFightLocked(frame))));
        fields.add(String.valueOf(squad.getRetreatLockedUntilFrame()));
        fields.add(String.valueOf(squad.getFightLockedUntilFrame()));
        fields.add(String.valueOf(SquadDecision.tristate(squad.isCommitted())));
        fields.add(String.valueOf(squad.isCommitted() ? squad.getCommitFrame() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(context.getShouldContain()));
        fields.add(String.valueOf(context.getCanBreakContainment()));
        fields.add(String.valueOf(context.getContainmentEntered()));
        fields.add(String.valueOf(center != null ? center.getX() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(center != null ? center.getY() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(groundDistanceToBase));
        return fields;
    }

    /**
     * Builds the two cells that describe the row's place in a RALLY episode.
     *
     * <p>The reason is sticky for as long as the squad exists, so it is present on the row that
     * opens the episode and on the row that closes it. Reading it is how a metric drops the
     * Defiler only squads that SquadManager rallies every frame by design.
     *
     * @param reason branch that last sent this squad to the rally point
     * @param release term that ended the episode, NONE on a row that does not end one
     */
    static List<String> rallyCells(RallyReason reason, RallyRelease release) {
        List<String> fields = new ArrayList<>();
        fields.add(reason.name());
        fields.add(release.name());
        return fields;
    }

    /**
     * Builds the worker defence cells. Every cell is -1 when the row is not a defence row, and the
     * simulation cells are -1 when no simulation ran.
     *
     * @param candidates gatherers that could have been pulled
     * @param pulled gatherers pulled on this row
     * @param released defenders released to mine on this row
     * @param sim full commitment simulation, or null when none ran
     */
    static List<String> defenseCells(int candidates, int pulled, int released, DefenseSim sim) {
        boolean simulated = sim != null && sim.isSimulated();
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(candidates));
        fields.add(String.valueOf(pulled));
        fields.add(String.valueOf(released));
        fields.add(String.valueOf(simulated ? sim.getDefenders() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(simulated ? sim.getEnemies() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(simulated ? sim.getDefenderSurvivors() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(simulated ? sim.getEnemySurvivors() : SquadDecision.NOT_EVALUATED));
        fields.add(simulated ? Csv.format(sim.getThreshold()) : String.valueOf(SquadDecision.NOT_EVALUATED));
        return fields;
    }

    /**
     * Builds the cell naming the branch that decided the status this row reports.
     *
     * <p>NONE on a row no branch claimed, which is every worker defence row and every terminal row,
     * both of which build their own context.
     *
     * @param context decision accumulated for the squad this frame
     * @return the decision path cell
     */
    static List<String> pathCells(SquadDecision context) {
        List<String> fields = new ArrayList<>();
        fields.add(Csv.name(context.getDecisionPath()));
        return fields;
    }

    /**
     * Builds the three cells that locate the containment arc a CONTAIN squad is holding.
     *
     * @param squad squad the row describes
     * @return arc center x, arc center y and the arc points, or the not evaluated sentinels
     */
    static List<String> arcCells(Squad squad) {
        List<String> fields = new ArrayList<>();
        Arc arc = squad.getStatus() == SquadStatus.CONTAIN ? squad.getContainmentArc() : null;
        if (arc == null) {
            fields.add(String.valueOf(SquadDecision.NOT_EVALUATED));
            fields.add(String.valueOf(SquadDecision.NOT_EVALUATED));
            fields.add(NONE);
            return fields;
        }
        List<String> points = new ArrayList<>();
        for (Position point : arc.getPositions()) {
            points.add(point.getX() + ":" + point.getY());
        }
        fields.add(String.valueOf(arc.getCenter().getX()));
        fields.add(String.valueOf(arc.getCenter().getY()));
        fields.add(String.join(";", points));
        return fields;
    }

    /**
     * Returns the ground path length from the squad centroid to the closest base held. Returns a
     * negative value when no base is reachable on the ground, for example for an air squad over
     * unwalkable terrain.
     */
    private int groundDistanceToNearestBase(Position center) {
        if (center == null) {
            return SquadDecision.NOT_EVALUATED;
        }

        int best = SquadDecision.NOT_EVALUATED;
        for (Position basePosition : gameState.getBaseData().getMyBasePositions()) {
            int length = gameState.getBwem().getMap().getPathLength(center, basePosition);
            if (length < 0) {
                continue;
            }
            if (best < 0 || length < best) {
                best = length;
            }
        }
        return best;
    }
}
