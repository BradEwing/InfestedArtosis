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
 * Records why a squad changed status, not just that it did.
 *
 * <p>Two events reach the file. A STATUS_CHANGE row is emitted by the end of frame sweep whenever a
 * fight squad holds a different {@link SquadStatus} than it did on the previous sweep, which covers
 * every path that can move a squad - the combat sim, containment entry and exit, rallying, merges
 * and splits - without instrumenting each setter. A LOCK_SUPPRESSED row is emitted when a
 * hysteresis lock held a squad on its current status after the simulator asked for the other one.
 *
 * <p>Locks are held for whole seconds, so an unfiltered suppression row would repeat every frame
 * for the life of the lock. Rows are deduplicated on the lock, its expiry frame and the overridden
 * verdict: one row per suppression episode, which is the unit a batch counts.
 *
 * <p>Read-only against combat code. Everything carried here is state the bot already computed;
 * HorizonCombatSimulator stashes its snapshot on every evaluate call regardless of any debug flag,
 * so reading it costs nothing and changes no decision.
 *
 * <p>Constructed only when combat telemetry is enabled, so the disabled bot allocates no writer,
 * registers no sink and opens no file handle.
 */
public class SquadDecisionLogger implements SquadDecisionSink {

    static final String FILE = "telemetry_squad_decisions.csv";

    static final String HEADER = "game_id,frame,squad_id,squad_type,event,old_status,new_status,sim_result,"
            + "suppressed_by,our_supply_real,squad_size,enemy_supply_believed_real,enemy_scouted,sim_our_strength,"
            + "sim_enemy_strength,sim_ratio,sim_engage_threshold,retreat_locked,fight_locked,"
            + "retreat_lock_until_frame,fight_lock_until_frame,should_contain,can_break_containment,"
            + "containment_entered,centroid_x,centroid_y,ground_distance_to_base";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final String EVENT_STATUS_CHANGE = "STATUS_CHANGE";
    private static final String EVENT_LOCK_SUPPRESSED = "LOCK_SUPPRESSED";
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
                    squad.getStatus(), decision, lock));
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

    /**
     * Telemetry never gets to end a game. An exception escaping onFrame kills the JVM and the batch
     * harness scores that as a crash, so a broken logger stops logging instead of propagating, and
     * unregisters itself so the hot path drops back to a single null check.
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
     * Emits a row for every fight squad whose status moved since the last sweep, then drops the
     * bookkeeping for squads that no longer exist. Merges and splits mint fresh squad ids, so a
     * squad appearing for the first time is reported as a transition out of NONE.
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
            writer.append(row(squad, frame, EVENT_STATUS_CHANGE, previous, current, decisions.get(id), null));
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
     * Supply of the enemy units the simulator counted, fog of war entries included. Paired with
     * sim_enemy_strength this separates a real unfavourable ratio from the divide by near zero an
     * unscouted enemy produces.
     */
    private int believedEnemySupply(HorizonCombatSimulator.DebugSnapshot snapshot) {
        int supply = 0;
        for (HorizonCombatSimulator.UnitDebugEntry entry : snapshot.getEnemyUnits()) {
            supply += entry.getType().supplyRequired();
        }
        return supply;
    }

    /**
     * Whether the lock actually held back a transition. A lock that pins the status the simulator
     * already wants suppressed nothing, and would otherwise flood the file with rows that answer
     * no question.
     *
     * @param status status the squad currently holds
     * @param lock lock that took the early return
     * @param result verdict the simulator returned this frame
     * @return true if the verdict asked for the other status
     */
    static boolean overridesVerdict(SquadStatus status, SquadLock lock,
                                    CombatSimulator.CombatResult result) {
        if (lock == SquadLock.RETREAT) {
            return status == SquadStatus.RETREAT && result == CombatSimulator.CombatResult.ENGAGE;
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
                       SquadDecision decision, SquadLock lock) {
        SquadDecision context = decision != null ? decision : new SquadDecision();
        Position center = squad.getCenter();

        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(frame));
        fields.add(squad.getId());
        fields.add(squadType(squad));
        fields.add(event);
        fields.add(Csv.name(from));
        fields.add(Csv.name(to));
        fields.add(context.getResult() != null ? context.getResult().name() : NONE);
        fields.add(lock != null ? lock.name() : NONE);
        fields.add(Csv.halfSupply(squad.getSupply()));
        fields.add(String.valueOf(squad.size()));
        fields.add(context.getEnemySupplyBelieved() < 0
                ? String.valueOf(SquadDecision.NOT_EVALUATED)
                : Csv.halfSupply(context.getEnemySupplyBelieved()));
        fields.add(String.valueOf(SquadDecision.tristate(gameState.getScoutData().isEnemyBuildingLocationKnown())));
        fields.add(Csv.format(context.getOurStrength()));
        fields.add(Csv.format(context.getEnemyStrength()));
        fields.add(Csv.format(context.getRatio()));
        fields.add(Csv.format(context.getEngageThreshold()));
        fields.add(String.valueOf(SquadDecision.tristate(context.isRetreatLocked())));
        fields.add(String.valueOf(SquadDecision.tristate(context.isFightLocked())));
        fields.add(String.valueOf(squad.getRetreatLockedUntilFrame()));
        fields.add(String.valueOf(squad.getFightLockedUntilFrame()));
        fields.add(String.valueOf(context.getShouldContain()));
        fields.add(String.valueOf(context.getCanBreakContainment()));
        fields.add(String.valueOf(context.getContainmentEntered()));
        fields.add(String.valueOf(center != null ? center.getX() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(center != null ? center.getY() : SquadDecision.NOT_EVALUATED));
        fields.add(String.valueOf(groundDistanceToNearestBase(center)));
        return String.join(",", fields);
    }

    /**
     * Ground path length from the squad centroid to the closest base we hold, so home defence and
     * away offence separate without joining rows against build tiles. Negative when no base is
     * reachable on the ground, which is the normal answer for an air squad over unwalkable terrain.
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
