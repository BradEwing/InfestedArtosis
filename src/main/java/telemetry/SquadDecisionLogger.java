package telemetry;

import bwapi.Game;
import bwapi.Position;
import info.GameState;
import unit.squad.CombatSimulator;
import unit.squad.Squad;
import unit.squad.SquadManager;
import unit.squad.SquadStatus;
import unit.squad.horizon.HorizonCombatSimulator;

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
 * out floor; suppressed_by carries MOVE_OUT_FLOOR on those rows.
 *
 * <p>LOCK_SUPPRESSED rows are deduplicated per suppression episode, keyed on the lock, its expiry
 * frame, and the overridden verdict.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class SquadDecisionLogger implements SquadDecisionSink {

    static final String FILE = "telemetry_squad_decisions.csv";

    static final String HEADER = "game_id,frame,squad_id,squad_type,event,old_status,new_status,sim_result,"
            + "suppressed_by,our_supply_real,squad_size,enemy_supply_believed_real,enemy_scouted,sim_our_strength,"
            + "sim_enemy_strength,sim_ratio,sim_engage_threshold,retreat_locked,fight_locked,"
            + "retreat_lock_until_frame,fight_lock_until_frame,committed,commit_frame,should_contain,"
            + "can_break_containment,containment_entered,centroid_x,centroid_y,ground_distance_to_base";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final String EVENT_STATUS_CHANGE = "STATUS_CHANGE";
    private static final String EVENT_LOCK_SUPPRESSED = "LOCK_SUPPRESSED";
    private static final String EVENT_SPLIT_SUPPRESSED = "SPLIT_SUPPRESSED";
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
            decision.setRetreatLocked(retreatLocked);
            decision.setFightLocked(fightLocked);
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

            String episode = lock.name() + "@" + lockUntilFrame(squad, lock) + ":" + decision.getResult();
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

    /**
     * Disables the logger and clears its state after an error, so a telemetry failure cannot crash the game.
     */
    private void disable() {
        disabled = true;
        decisions.clear();
        lastStatus.clear();
        lastSuppression.clear();
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
            SquadStatus current = squad.getStatus();
            SquadStatus previous = lastStatus.get(id);
            if (current == previous) {
                continue;
            }
            lastStatus.put(id, current);
            writer.append(row(squad, frame, EVENT_STATUS_CHANGE, previous, current, decisions.get(id), NONE));
        }

        lastStatus.keySet().retainAll(present);
        lastSuppression.keySet().retainAll(present);
        decisions.clear();
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
        SquadDecision context = decision != null ? decision : new SquadDecision();
        List<String> fields = new ArrayList<>(identityCells(gameId, frame, squad, event, from, to, context,
                suppressedBy));
        fields.addAll(squadCells(squad, context,
                gameState.getScoutData().isEnemyBuildingLocationKnown(),
                groundDistanceToNearestBase(squad.getCenter())));
        return String.join(",", fields);
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
     * Builds the cells that measure the squad at decision time, from our_supply_real through
     * ground_distance_to_base. commit_frame is -1 when the squad is not committed, matching the
     * {@link SquadDecision#NOT_EVALUATED} sentinel used by the sim columns.
     */
    static List<String> squadCells(Squad squad, SquadDecision context, boolean enemyScouted,
                                   int groundDistanceToBase) {
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
        fields.add(String.valueOf(SquadDecision.tristate(context.isRetreatLocked())));
        fields.add(String.valueOf(SquadDecision.tristate(context.isFightLocked())));
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
