package telemetry;

import bwapi.Game;
import bwapi.Player;
import bwapi.TilePosition;
import info.GameState;
import info.ResourceCount;
import learning.GameRecord;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSite;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes one CSV row per plan lifecycle event to bwapi-data/write, so a game's build execution can
 * be reconstructed after the fact from files alone.
 *
 * <p>Two files are produced per game, both prefixed telemetry_ so the batch harness excludes them
 * from its learning-row glob: telemetry_plans_&lt;stamp&gt;.csv holds the events and
 * telemetry_game_&lt;stamp&gt;.csv holds a single {@link GameRecord} row describing the game. The
 * summary is written at onEnd only, so its absence marks a game the bot did not survive.
 *
 * <p>Four events are emitted. ENQUEUE when a plan first reaches the production queue; TRANSITION
 * on every real state change; BLOCKED when a wait ends, naming the blocker that just ended and how
 * long it lasted; and OPEN_AT_GAME_END for every plan still alive when the game stops, so an open
 * plan is never mistaken for a lost row.
 *
 * <p>A cancellation row carries cancel_reason and cancel_site. The site is the predicate that
 * destroyed the plan and the reason is the canonical category it cancels for, so grouping by
 * either answers a different question about the same row. Both are empty on non-cancellation
 * rows. UNKNOWN means a cancelling call site failed to name itself, not that the cause is
 * mysterious.
 *
 * <p>Supply is reported in real supply, halves included. BWAPI counts supply in half units; build
 * orders are named by real supply, hence the supply_used_real and supply_total_real column names.
 *
 * <p>The bank and the available figures are both recorded because scheduling gates on available
 * resources, and only the pair distinguishes a broke bot from an over-reserved one.
 *
 * <p><b>Snapshot semantics.</b> Every count on a row (larva, gatherers, queue_depth,
 * plans_scheduled, plans_building, plans_morphing, minerals, gas) is read at the instant the hook
 * fires, not before the event. On an ENQUEUE row the plan is already in the queue, so queue_depth
 * includes it. On a TRANSITION row the plan is mid-move: the manager removes it from the old
 * collection and adds it to the new one around the state assignment, so the plan is typically
 * counted in neither plans_scheduled nor plans_building for that one row. Read the counts as the
 * state of the rest of the bot, not as a consistent before or after picture of this plan.
 */
public class PlanEventLogger implements PlanEventSink {

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private static final String EVENT_ENQUEUE = "ENQUEUE";
    private static final String EVENT_TRANSITION = "TRANSITION";
    private static final String EVENT_BLOCKED = "BLOCKED";
    private static final String EVENT_OPEN_AT_GAME_END = "OPEN_AT_GAME_END";

    private static final String PLAN_HEADER = "frame,time,event,plan_id,plan_type,item,from_state,to_state,"
            + "cancel_reason,cancel_site,blocker,blocked_frames,priority,frames_in_state,age_frames,minerals,gas,"
            + "available_minerals,available_gas,supply_used_real,supply_total_real,larva,gatherers,queue_depth,"
            + "plans_scheduled,plans_building,plans_morphing,build_tile_x,build_tile_y,macro_hatchery,build_order";

    private static final String GAME_HEADER = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,"
            + "opponent_race,opener,build_order,detected_strategies,frame_count";

    private final Game game;
    private final GameState gameState;
    private final String openerName;
    private final int numStartingLocations;

    private final TelemetryWriter planWriter;
    private final TelemetryWriter gameWriter;

    private final Map<String, PlanTrace> traces = new HashMap<>();
    private final Map<String, Plan> openPlans = new LinkedHashMap<>();
    private final List<String> buffer = new ArrayList<>();

    private boolean disabled;
    private int currentFrame;
    private int lastFlushFrame;

    public PlanEventLogger(Game game, GameState gameState, String openerName, int numStartingLocations) {
        this.game = game;
        this.gameState = gameState;
        this.openerName = openerName;
        this.numStartingLocations = numStartingLocations;

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now());
        this.planWriter = new TelemetryWriter("telemetry_plans_" + stamp + ".csv", PLAN_HEADER);
        this.gameWriter = new TelemetryWriter("telemetry_game_" + stamp + ".csv", GAME_HEADER);
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            if (currentFrame - lastFlushFrame >= FLUSH_INTERVAL_FRAMES) {
                flush();
                lastFlushFrame = currentFrame;
            }
        } catch (Exception e) {
            disabled = true;
        }
    }

    public void onEnd(boolean isWinner) {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            closeOpenPlans();
            flush();
            gameWriter.append(Collections.singletonList(gameRecord(isWinner).toCsvRow()));
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onEnqueue(Plan plan) {
        if (disabled) {
            return;
        }

        try {
            if (traces.containsKey(plan.getUuid())) {
                return;
            }
            PlanTrace trace = newTrace(plan);
            buffer.add(row(plan, trace, EVENT_ENQUEUE, null, plan.getState(), PlanBlocker.NONE, 0));
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onStateChange(Plan plan, PlanState from, PlanState to) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(plan);
            endBlocker(plan, trace);
            buffer.add(row(plan, trace, EVENT_TRANSITION, from, to, PlanBlocker.NONE, 0));
            trace.setLastStateFrame(currentFrame);
            if (to == PlanState.COMPLETE || to == PlanState.CANCELLED) {
                openPlans.remove(plan.getUuid());
            }
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onBlocked(Plan plan, PlanBlocker blocker) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(plan);
            if (trace.getBlocker() == blocker) {
                return;
            }
            endBlocker(plan, trace);
            trace.startBlocker(blocker, currentFrame);
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Emits a BLOCKED row for the wait that is ending and returns the trace to unblocked. A wait
     * is only measurable once it is over, so nothing is written while it is still running.
     */
    private void endBlocker(Plan plan, PlanTrace trace) {
        PlanBlocker blocker = trace.getBlocker();
        if (blocker == PlanBlocker.NONE) {
            return;
        }
        int waited = currentFrame - trace.getBlockerSinceFrame();
        buffer.add(row(plan, trace, EVENT_BLOCKED, null, null, blocker, waited));
        trace.clearBlocker(currentFrame);
    }

    /**
     * Marks every plan still alive at the final frame, so a plan with no terminal row is a genuine
     * gap in the log rather than a game that simply ended first.
     */
    private void closeOpenPlans() {
        for (Map.Entry<String, Plan> entry : openPlans.entrySet()) {
            Plan plan = entry.getValue();
            PlanTrace trace = traces.get(entry.getKey());
            if (trace == null) {
                continue;
            }
            endBlocker(plan, trace);
            buffer.add(row(plan, trace, EVENT_OPEN_AT_GAME_END, null, plan.getState(), PlanBlocker.NONE, 0));
        }
        openPlans.clear();
    }

    private PlanTrace trace(Plan plan) {
        PlanTrace trace = traces.get(plan.getUuid());
        if (trace == null) {
            return newTrace(plan);
        }
        return trace;
    }

    private PlanTrace newTrace(Plan plan) {
        PlanTrace trace = new PlanTrace(currentFrame);
        traces.put(plan.getUuid(), trace);
        openPlans.put(plan.getUuid(), plan);
        return trace;
    }

    private void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        planWriter.append(buffer);
        buffer.clear();
        if (planWriter.isDisabled()) {
            disabled = true;
        }
    }

    private String row(Plan plan, PlanTrace trace, String event, PlanState from, PlanState to,
                       PlanBlocker blocker, int blockedFrames) {
        Player self = gameState.getSelf();
        ResourceCount resourceCount = gameState.getResourceCount();
        TilePosition buildPosition = plan.getBuildPosition();
        boolean cancelled = to == PlanState.CANCELLED;
        PlanCancelSite cancelSite = plan.getCancelSite();

        StringBuilder sb = new StringBuilder();
        sb.append(currentFrame).append(',');
        sb.append(new Time(currentFrame)).append(',');
        sb.append(event).append(',');
        sb.append(plan.getPlanId()).append(',');
        sb.append(planType(plan)).append(',');
        sb.append(Csv.sanitize(plan.getName())).append(',');
        sb.append(from == null ? "" : from.toString()).append(',');
        sb.append(to == null ? "" : to.toString()).append(',');
        sb.append(cancelled ? plan.getCancelReason().toString() : "").append(',');
        sb.append(cancelled && cancelSite != null ? cancelSite.toString() : "").append(',');
        sb.append(blocker == PlanBlocker.NONE ? "" : blocker.toString()).append(',');
        sb.append(blocker == PlanBlocker.NONE ? "" : String.valueOf(blockedFrames)).append(',');
        sb.append(plan.getPriority()).append(',');
        sb.append(currentFrame - trace.getLastStateFrame()).append(',');
        sb.append(currentFrame - trace.getEnqueueFrame()).append(',');
        sb.append(self.minerals()).append(',');
        sb.append(self.gas()).append(',');
        sb.append(resourceCount.availableMinerals()).append(',');
        sb.append(resourceCount.availableGas()).append(',');
        sb.append(Csv.halfSupply(self.supplyUsed())).append(',');
        sb.append(Csv.halfSupply(self.supplyTotal())).append(',');
        sb.append(gameState.numLarva()).append(',');
        sb.append(gameState.numGatherers()).append(',');
        sb.append(gameState.getProductionQueue().size()).append(',');
        sb.append(gameState.getPlansScheduled().size()).append(',');
        sb.append(gameState.getPlansBuilding().size()).append(',');
        sb.append(gameState.getPlansMorphing().size()).append(',');
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getX())).append(',');
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getY())).append(',');
        sb.append(plan.isMacroHatchery()).append(',');
        sb.append(Csv.sanitize(activeBuildOrderName()));
        return sb.toString();
    }

    private String planType(Plan plan) {
        PlanType type = plan.getType();
        return type == null ? "" : type.toString();
    }

    private String activeBuildOrderName() {
        BuildOrder buildOrder = gameState.getActiveBuildOrder();
        return buildOrder == null ? "" : buildOrder.getName();
    }

    private GameRecord gameRecord(boolean isWinner) {
        return GameRecord.builder()
                .timestamp(System.currentTimeMillis())
                .numStartingLocations(numStartingLocations)
                .mapName(game.mapFileName())
                .opponentName(game.enemy().getName())
                .opponentRace(gameState.getOpponentRace() == null ? "" : gameState.getOpponentRace().toString())
                .opener(openerName)
                .buildOrder(activeBuildOrderName())
                .detectedStrategies(gameState.getStrategyTracker() == null
                        ? "" : gameState.getStrategyTracker().getDetectedStrategiesAsString())
                .isWinner(isWinner)
                .frameCount(currentFrame)
                .build();
    }
}
