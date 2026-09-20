package telemetry;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import info.BuilderThreat;
import info.GameState;
import info.ResourceCount;
import learning.GameRecord;
import macro.plan.BuilderDispatchDecision;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.LarvaBoundMacroHatchery;
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

/** Writes plan lifecycle events and a game summary to CSV files. */
public class PlanEventLogger implements PlanEventSink {

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private static final String EVENT_ENQUEUE = "ENQUEUE";
    private static final String EVENT_TRANSITION = "TRANSITION";
    private static final String EVENT_BLOCKED = "BLOCKED";
    private static final String EVENT_STALE = "STALE";
    private static final String EVENT_OPEN_AT_GAME_END = "OPEN_AT_GAME_END";
    private static final String EVENT_BUILD_AHEAD_HOLD = "BUILD_AHEAD_HOLD";
    private static final String EVENT_BUILD_AHEAD_EVICT = "BUILD_AHEAD_EVICT";
    private static final String EVENT_BUILD_AHEAD_YIELD = "BUILD_AHEAD_YIELD";
    private static final String EVENT_WITHHELD = "WITHHELD";
    private static final String EVENT_UNPLANNED_CANCEL = "UNPLANNED_CANCEL";
    private static final String EVENT_BLOCKER_DIVERT = "BLOCKER_DIVERT";
    private static final String EVENT_MACRO_HATCHERY_TRIGGER = "MACRO_HATCHERY_TRIGGER";
    private static final String EVENT_MACRO_HATCHERY_WITHHELD = "MACRO_HATCHERY_WITHHELD";
    private static final String EVENT_BUILDER_DISPATCH_DECISION = "BUILDER_DISPATCH_DECISION";
    private static final String EVENT_EXPANSION_BACKOFF = "EXPANSION_BACKOFF";

    private static final int NO_STARVED_COUNT = -1;

    private static final String EVENT_RECURRING_CANCEL = "RECURRING_CANCEL";

    /**
     * 48 columns; readers that index by position rather than by name must match this order.
     * enemy_air, gas_gathered, enemy_barracks, the blocker mineral pair, the enemy ground pair,
     * yield_to_plan_id and the four macro hatchery gate columns are trailing columns written by
     * {@link #appendTrailing}, so every row shape keeps one width.
     * <p>
     * blocker_mineral_x and blocker_mineral_y are the pixel position of the mineral a stalled
     * builder was sent to mine, set only on BLOCKER_DIVERT rows.
     * <p>
     * enemy_ground_known_at_bases and enemy_ground_visible_at_bases are the two counts of enemy
     * mobile ground combat units at our bases that the rush defence reads: by last known position,
     * and by what is visible this frame.
     * <p>
     * yield_to_plan_id is the emergency defence plan a holder gave the build-ahead slot to, set only
     * on BUILD_AHEAD_YIELD rows.
     * <p>
     * macro_hatchery_gate, hatcheries, macro_tech_ready and macro_hatcheries_outstanding are set
     * only on MACRO_HATCHERY_TRIGGER and MACRO_HATCHERY_WITHHELD rows: the gate the larva-bound
     * macro hatchery request stopped on, and the inputs the row's other columns do not carry. The
     * larva, available_minerals, available_gas and enemy_ground_known_at_bases columns on those
     * rows are the request's other inputs.
     * <p>
     * enemy_barracks is the living observed count the sunken floors read, on the plan row rather
     * than the game summary, so a batch can date a colony plan against the Barracks known at the
     * frame it was queued.
     * <p>
     * larva, assigned_larva and reserved_larva are three terms of one sum, not three views of it.
     * A larva handed to a plan leaves the larva set while its reservation stands, so larva free
     * for another plan is {@code larva + assigned_larva - reserved_larva}, which is the arithmetic
     * {@link info.ResourceCount#canScheduleLarva} applies.
     * <p>
     * builder_route_enemies, builder_site_enemies, builder_route_defense_zones and builder_at_site
     * are what a builder would walk into, written on every BUILDING row that has an executor and
     * read fresh on the row's own frame. builder_dispatch_decision is what the gate did with it,
     * set only on BUILDER_DISPATCH_DECISION rows.
     * <p>
     * lost_expansion_builders and expansion_hold_until_frame are set only on EXPANSION_BACKOFF
     * rows. The hold a row armed is expansion_hold_until_frame minus frame.
     */
    static final String PLAN_HEADER = "frame,time,event,plan_id,executor_unit_id,plan_type,item,from_state,"
            + "to_state,cancel_reason,cancel_source,blocker,blocked_frames,priority,frames_in_state,age_frames,"
            + "minerals,gas,available_minerals,available_gas,supply_used_real,supply_total_real,larva,assigned_larva,"
            + "reserved_larva,gatherers,queue_depth,plans_scheduled,plans_building,plans_morphing,build_tile_x,"
            + "build_tile_y,macro_hatchery,build_order,starved_behind,builder_distance_px,enemy_air,gas_gathered,"
            + "enemy_barracks,blocker_mineral_x,blocker_mineral_y,enemy_ground_known_at_bases,"
            + "enemy_ground_visible_at_bases,yield_to_plan_id,macro_hatchery_gate,hatcheries,macro_tech_ready,"
            + "macro_hatcheries_outstanding,builder_route_enemies,builder_site_enemies,"
            + "builder_route_defense_zones,builder_at_site,builder_dispatch_decision,lost_expansion_builders,"
            + "expansion_hold_until_frame";

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
    private final Map<String, PlanTrace> withheld = new LinkedHashMap<>();
    private final List<String> buffer = new ArrayList<>();

    private final Map<String, PlanRecurrence> recurrences = new HashMap<>();

    private LarvaBoundMacroHatchery.Gate lastMacroHatcheryGate;

    private final Map<String, BuilderDispatchDecision> lastDispatchDecisions = new HashMap<>();

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
            endWithheld(plan.getName());
            PlanTrace trace = newTrace(plan);
            buffer.add(row(plan, trace, EVENT_ENQUEUE, null, plan.getState(), PlanBlocker.NONE, 0, NO_STARVED_COUNT));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /** Opens a withheld interval per unit type and writes one row when it closes. */
    @Override
    public void onWithheld(UnitType unitType, PlanBlocker blocker) {
        if (disabled) {
            return;
        }

        try {
            String item = unitType.toString();
            PlanTrace trace = withheld.get(item);
            if (trace != null && trace.getBlocker() == blocker) {
                return;
            }
            endWithheld(item);
            PlanTrace opened = new PlanTrace(currentFrame);
            opened.startBlocker(blocker, currentFrame);
            withheld.put(item, opened);
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Records a cancellation issued straight at a unit, with no plan behind it. Without this the
     * reaction that cancels an extractor morph after its plan has already left every plan set leaves
     * no trace at all in the plan log.
     */
    @Override
    public void onUnplannedCancel(UnitType unitType, PlanCancelSource cancelSource) {
        if (disabled) {
            return;
        }

        try {
            buffer.add(unplannedCancelRow(unitType.toString(), cancelSource));
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
            buffer.add(row(plan, trace, EVENT_TRANSITION, from, to, PlanBlocker.NONE, 0, NO_STARVED_COUNT));
            trace.setLastStateFrame(currentFrame);
            trace.clearStaleReported();
            if (to == PlanState.COMPLETE || to == PlanState.CANCELLED) {
                openPlans.remove(plan.getUuid());
            }
            if (to == PlanState.CANCELLED && plan.getCancelSource() != null && recordRecurrence(plan)) {
                buffer.add(row(plan, trace, EVENT_RECURRING_CANCEL, from, to, PlanBlocker.NONE, 0, NO_STARVED_COUNT));
            }
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes a BLOCKED row only when the blocker changes, so one row covers the whole interval the
     * plan spent on the previous blocker. Its frame and every game state column are therefore the
     * <em>end</em> of that interval, not its start; subtract blocked_frames to reach the start.
     */
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
     * Writes one STALE row per stint in PLANNED, on the first scan past the stale threshold, so a
     * plan that is never attempted shows up before a late cancel closes its blocker interval. The
     * blocker and blocked_frames columns carry the open interval so far without closing it.
     */
    @Override
    public void onStale(Plan plan) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(plan);
            if (!trace.markStaleReported()) {
                return;
            }
            PlanBlocker blocker = trace.getBlocker();
            int waited = blocker == PlanBlocker.NONE ? 0 : currentFrame - trace.getBlockerSinceFrame();
            buffer.add(row(plan, trace, EVENT_STALE, null, plan.getState(), blocker, waited, NO_STARVED_COUNT));
        } catch (Exception e) {
            disabled = true;
        }
    }

    @Override
    public void onBuildAheadHold(Plan holder, int heldFrames, int starvedBehind) {
        buildAheadRow(EVENT_BUILD_AHEAD_HOLD, holder, heldFrames, starvedBehind);
    }

    @Override
    public void onBuildAheadEvict(Plan holder, int heldFrames, int starvedBehind) {
        buildAheadRow(EVENT_BUILD_AHEAD_EVICT, holder, heldFrames, starvedBehind);
    }

    /**
     * Writes one row per holder that gave the slot to emergency defence, in the shape of a
     * BUILD_AHEAD_EVICT row: blocked_frames is how long the holder held the slot.
     */
    @Override
    public void onBuildAheadYield(Plan holder, int heldFrames, Plan emergency) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(holder);
            StringBuilder sb = planColumns(holder, trace, EVENT_BUILD_AHEAD_YIELD, null, holder.getState(),
                    PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, heldFrames, NO_STARVED_COUNT);
            appendTrailing(sb, null, emergency, null, builderThreat(holder), null, null);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row per divert, so a batch can tell a wall clear the stall started from one it
     * did not. builder_distance_px is the builder's distance to its site at the divert frame.
     */
    @Override
    public void onBlockerDivert(Plan plan, Position mineral) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(plan);
            StringBuilder sb = planColumns(plan, trace, EVENT_BLOCKER_DIVERT, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT);
            appendTrailing(sb, mineral, null, null, builderThreat(plan), null, null);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row each time the larva-bound macro hatchery request reaches a new gate while the
     * build is larva bound and floating both banks. A request that stops on one gate for many
     * frames writes one row, on the frame it reached that gate.
     */
    @Override
    public void onMacroHatcheryGate(LarvaBoundMacroHatchery.Gate gate, boolean techReady, int hatcheries,
                                    int outstandingMacroHatcheries) {
        if (disabled) {
            return;
        }

        try {
            if (gate == lastMacroHatcheryGate) {
                return;
            }
            lastMacroHatcheryGate = gate;
            if (!gate.isRequest()) {
                return;
            }
            buffer.add(macroHatcheryGateRow(new MacroHatcheryGateInputs(gate, techReady, hatcheries,
                    outstandingMacroHatcheries)));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row each time the dispatch gate reaches a new decision for a plan, so a builder
     * parked on the same threat for hundreds of frames writes one row, on the frame it was first
     * held. A recall always writes, because the decision before it was DISPATCH.
     */
    @Override
    public void onBuilderDispatchDecision(Plan plan, BuilderDispatchDecision decision, BuilderThreat threat) {
        if (disabled) {
            return;
        }

        try {
            if (lastDispatchDecisions.put(plan.getUuid(), decision) == decision) {
                return;
            }
            PlanTrace trace = trace(plan);
            StringBuilder sb = planColumns(plan, trace, EVENT_BUILDER_DISPATCH_DECISION, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT);
            appendTrailing(sb, null, null, null, threat, decision, null);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /** Writes one row per hold armed, with no plan behind it: the plan that armed it is already cancelled. */
    @Override
    public void onExpansionBackoff(int lostExpansionBuilders, int expansionHeldUntilFrame) {
        if (disabled) {
            return;
        }

        try {
            buffer.add(expansionBackoffRow(new ExpansionBackoffInputs(lostExpansionBuilders,
                    expansionHeldUntilFrame)));
        } catch (Exception e) {
            disabled = true;
        }
    }

    private void buildAheadRow(String event, Plan holder, int heldFrames, int starvedBehind) {
        if (disabled) {
            return;
        }

        try {
            PlanTrace trace = trace(holder);
            buffer.add(row(holder, trace, event, null, holder.getState(),
                    PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, heldFrames, starvedBehind));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Records a cancellation for this plan's item and cancel source, and reports whether the
     * count crossed the recurrence threshold.
     */
    private boolean recordRecurrence(Plan plan) {
        String key = plan.getName() + "|" + plan.getCancelSource();
        PlanRecurrence recurrence = recurrences.computeIfAbsent(key, any -> new PlanRecurrence());
        return recurrence.record(currentFrame);
    }

    private void endBlocker(Plan plan, PlanTrace trace) {
        PlanBlocker blocker = trace.getBlocker();
        if (blocker == PlanBlocker.NONE) {
            return;
        }
        int waited = currentFrame - trace.getBlockerSinceFrame();
        buffer.add(row(plan, trace, EVENT_BLOCKED, null, null, blocker, waited, NO_STARVED_COUNT));
        trace.clearBlocker(currentFrame);
    }

    private void endWithheld(String item) {
        PlanTrace trace = withheld.remove(item);
        if (trace == null) {
            return;
        }
        int waited = currentFrame - trace.getBlockerSinceFrame();
        buffer.add(withheldRow(item, trace.getBlocker(), waited));
    }

    private void closeOpenPlans() {
        for (Map.Entry<String, Plan> entry : openPlans.entrySet()) {
            Plan plan = entry.getValue();
            PlanTrace trace = traces.get(entry.getKey());
            if (trace == null) {
                continue;
            }
            endBlocker(plan, trace);
            buffer.add(row(plan, trace, EVENT_OPEN_AT_GAME_END, null, plan.getState(), PlanBlocker.NONE, 0, NO_STARVED_COUNT));
        }
        openPlans.clear();
        for (String item : new ArrayList<>(withheld.keySet())) {
            endWithheld(item);
        }
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
                       PlanBlocker blocker, int blockedFrames, int starvedBehind) {
        StringBuilder sb = planColumns(plan, trace, event, from, to, blocker, blockedFrames, starvedBehind);
        appendTrailing(sb, null, null, null, builderThreat(plan), null, null);
        return sb.toString();
    }

    /** Every plan-row column up to and including builder_distance_px. */
    private StringBuilder planColumns(Plan plan, PlanTrace trace, String event, PlanState from, PlanState to,
                                      PlanBlocker blocker, int blockedFrames, int starvedBehind) {
        TilePosition buildPosition = plan.getBuildPosition();
        boolean cancelled = to == PlanState.CANCELLED;
        PlanCancelSource cancelSource = plan.getCancelSource();
        Unit executor = gameState.executorOf(plan);

        StringBuilder sb = new StringBuilder();
        appendEvent(sb, event);
        sb.append(plan.getPlanId()).append(',');
        sb.append(executor == null ? "" : String.valueOf(executor.getID())).append(',');
        sb.append(planType(plan)).append(',');
        sb.append(Csv.sanitize(plan.getName())).append(',');
        sb.append(from == null ? "" : from.toString()).append(',');
        sb.append(to == null ? "" : to.toString()).append(',');
        sb.append(cancelled ? plan.getCancelReason().toString() : "").append(',');
        sb.append(cancelled && cancelSource != null ? cancelSource.toString() : "").append(',');
        appendBlocker(sb, blocker, blockedFrames);
        sb.append(plan.getPriority()).append(',');
        sb.append(currentFrame - trace.getLastStateFrame()).append(',');
        sb.append(currentFrame - trace.getEnqueueFrame()).append(',');
        appendGameState(sb);
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getX())).append(',');
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getY())).append(',');
        sb.append(plan.isMacroHatchery()).append(',');
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        sb.append(starvedBehind == NO_STARVED_COUNT ? "" : String.valueOf(starvedBehind)).append(',');
        sb.append(builderDistance(executor, buildPosition)).append(',');
        return sb;
    }

    /**
     * How far the executor still is from the tile it was sent to, in pixels. Blank until both the
     * executor and the build position are known, which is what separates a builder still walking
     * from one that arrived and could not place.
     */
    private String builderDistance(Unit executor, TilePosition buildPosition) {
        if (executor == null || buildPosition == null) {
            return "";
        }
        return String.valueOf(executor.getDistance(buildPosition.toPosition()));
    }

    /** A row for a unit the build order wanted but never planned, so the plan columns are empty. */
    private String withheldRow(String item, PlanBlocker blocker, int withheldFrames) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_WITHHELD);
        appendEmpty(sb, 2);
        sb.append(PlanType.UNIT).append(',');
        sb.append(Csv.sanitize(item)).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, blocker, withheldFrames);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 3);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, null, null, null);
        return sb.toString();
    }

    /** A row for a macro hatchery request, which has no plan behind it, so the plan columns are empty. */
    private String macroHatcheryGateRow(MacroHatcheryGateInputs inputs) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, inputs.gate == LarvaBoundMacroHatchery.Gate.TRIGGER
                ? EVENT_MACRO_HATCHERY_TRIGGER : EVENT_MACRO_HATCHERY_WITHHELD);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(UnitType.Zerg_Hatchery.toString())).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 2);
        sb.append(true).append(',');
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, inputs, null, null, null);
        return sb.toString();
    }

    /** A row for a unit cancelled outside the plan system, so the plan columns are empty. */
    private String unplannedCancelRow(String item, PlanCancelSource cancelSource) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_UNPLANNED_CANCEL);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(item)).append(',');
        appendEmpty(sb, 1);
        sb.append(PlanState.CANCELLED).append(',');
        sb.append(cancelSource.getReason()).append(',');
        sb.append(cancelSource).append(',');
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 3);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 1);
        appendEmpty(sb, 1);
        appendTrailing(sb, null, null, null, null, null, null);
        return sb.toString();
    }

    /** A row for a hold on expanding, which no plan owns, so the plan columns are empty. */
    private String expansionBackoffRow(ExpansionBackoffInputs inputs) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_EXPANSION_BACKOFF);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(UnitType.Zerg_Hatchery.toString())).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 3);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, null, null, inputs);
        return sb.toString();
    }

    /**
     * What the builder for this plan would walk into, or null when the plan has no builder to read
     * it for. Recomputed per row rather than carried from the last gate evaluation, so a row taken
     * between two evaluations reports the frame it was written on.
     *
     * <p>Only a worker executor is read. A Lair, Hive or colony morph is a building plan with an
     * executor too, but its executor is the structure morphing in place: it walks nowhere, so a
     * route and a site reading for it would describe a walk that never happens.
     */
    private BuilderThreat builderThreat(Plan plan) {
        if (plan.getType() != PlanType.BUILDING) {
            return null;
        }
        Unit executor = gameState.executorOf(plan);
        if (executor == null || !executor.getType().isWorker()) {
            return null;
        }
        return gameState.builderThreat(plan.getBuildPosition(), executor.getTilePosition());
    }

    /**
     * The trailing columns, written by every row shape.
     *
     * <p>row, withheldRow and unplannedCancelRow build their middles independently, so a trailing
     * column added to one of them alone changes what a reader indexing by position finds in the
     * others. Every trailing column belongs here so all shapes keep the same width.
     *
     * @param sb the row being built
     * @param blockerMineral the diverted-to mineral's position, or null on every row but BLOCKER_DIVERT
     * @param yieldTo the emergency plan given the build-ahead slot, or null on every row but BUILD_AHEAD_YIELD
     * @param macroHatchery the macro hatchery request's gate and inputs, or null on every row but
     *     MACRO_HATCHERY_TRIGGER and MACRO_HATCHERY_WITHHELD
     * @param builderThreat what the plan's builder would walk into, or null when the row has no
     *     BUILDING plan with an executor behind it
     * @param decision what the dispatch gate did, or null on every row but BUILDER_DISPATCH_DECISION
     * @param backoff the hold armed, or null on every row but EXPANSION_BACKOFF
     */
    private void appendTrailing(StringBuilder sb, Position blockerMineral, Plan yieldTo,
                                MacroHatcheryGateInputs macroHatchery, BuilderThreat builderThreat,
                                BuilderDispatchDecision decision, ExpansionBackoffInputs backoff) {
        appendGameTotals(sb);
        sb.append(',');
        sb.append(blockerMineral == null ? "" : String.valueOf(blockerMineral.getX())).append(',');
        sb.append(blockerMineral == null ? "" : String.valueOf(blockerMineral.getY())).append(',');
        sb.append(gameState.knownEnemyMobileGroundCombatUnitsAtOurBases()).append(',');
        sb.append(gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases()).append(',');
        sb.append(yieldTo == null ? "" : String.valueOf(yieldTo.getPlanId())).append(',');
        sb.append(macroHatchery == null ? "" : macroHatchery.gate.toString()).append(',');
        sb.append(macroHatchery == null ? "" : String.valueOf(macroHatchery.hatcheries)).append(',');
        sb.append(macroHatchery == null ? "" : String.valueOf(macroHatchery.techReady)).append(',');
        sb.append(macroHatchery == null ? "" : String.valueOf(macroHatchery.outstandingMacroHatcheries)).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getRouteEnemies())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getSiteEnemies())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getRouteDefenseZones())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.isBuilderAtSite())).append(',');
        sb.append(decision == null ? "" : decision.toString()).append(',');
        sb.append(backoff == null ? "" : String.valueOf(backoff.lostExpansionBuilders)).append(',');
        sb.append(backoff == null ? "" : String.valueOf(backoff.expansionHeldUntilFrame));
    }

    /** The trailing cumulative columns. */
    private void appendGameTotals(StringBuilder sb) {
        sb.append(gameState.observedEnemyAirCombatUnitCount()).append(',');
        sb.append(gameState.getSelf().gatheredGas()).append(',');
        sb.append(gameState.enemyUnitCount(UnitType.Terran_Barracks));
    }

    private void appendEvent(StringBuilder sb, String event) {
        sb.append(currentFrame).append(',');
        sb.append(new Time(currentFrame)).append(',');
        sb.append(event).append(',');
    }

    private void appendBlocker(StringBuilder sb, PlanBlocker blocker, int blockedFrames) {
        sb.append(blocker == PlanBlocker.NONE ? "" : blocker.toString()).append(',');
        sb.append(blocker == PlanBlocker.NONE ? "" : String.valueOf(blockedFrames)).append(',');
    }

    private void appendGameState(StringBuilder sb) {
        Player self = gameState.getSelf();
        ResourceCount resourceCount = gameState.getResourceCount();
        sb.append(self.minerals()).append(',');
        sb.append(self.gas()).append(',');
        sb.append(resourceCount.availableMinerals()).append(',');
        sb.append(resourceCount.availableGas()).append(',');
        sb.append(Csv.halfSupply(self.supplyUsed())).append(',');
        sb.append(Csv.halfSupply(self.supplyTotal())).append(',');
        sb.append(gameState.numLarva()).append(',');
        sb.append(gameState.larvaAssignedToPlans()).append(',');
        sb.append(resourceCount.getReservedLarva()).append(',');
        sb.append(gameState.numGatherers()).append(',');
        sb.append(gameState.getProductionQueue().size()).append(',');
        sb.append(gameState.getPlansScheduled().size()).append(',');
        sb.append(gameState.getPlansBuilding().size()).append(',');
        sb.append(gameState.getPlansMorphing().size()).append(',');
    }

    private void appendEmpty(StringBuilder sb, int columns) {
        for (int i = 0; i < columns; i++) {
            sb.append(',');
        }
    }

    private String planType(Plan plan) {
        PlanType type = plan.getType();
        return type == null ? "" : type.toString();
    }

    private String activeBuildOrderName() {
        BuildOrder buildOrder = gameState.getActiveBuildOrder();
        return buildOrder == null ? "" : buildOrder.getName();
    }

    private String learningBuildOrderName() {
        return gameState.getBuildOrderChain().joinOrElse(openerName);
    }

    private GameRecord gameRecord(boolean isWinner) {
        return GameRecord.builder()
                .timestamp(System.currentTimeMillis())
                .numStartingLocations(numStartingLocations)
                .mapName(game.mapFileName())
                .opponentName(game.enemy().getName())
                .opponentRace(gameState.getOpponentRace() == null ? "" : gameState.getOpponentRace().toString())
                .opener(openerName)
                .buildOrder(learningBuildOrderName())
                .detectedStrategies(gameState.getStrategyTracker() == null
                        ? "" : gameState.getStrategyTracker().getDetectedStrategiesAsString())
                .isWinner(isWinner)
                .frameCount(currentFrame)
                .build();
    }

    /** The gate a macro hatchery request stopped on and the inputs no other row column carries. */
    private static final class MacroHatcheryGateInputs {
        private final LarvaBoundMacroHatchery.Gate gate;
        private final boolean techReady;
        private final int hatcheries;
        private final int outstandingMacroHatcheries;

        private MacroHatcheryGateInputs(LarvaBoundMacroHatchery.Gate gate, boolean techReady, int hatcheries,
                                        int outstandingMacroHatcheries) {
            this.gate = gate;
            this.techReady = techReady;
            this.hatcheries = hatcheries;
            this.outstandingMacroHatcheries = outstandingMacroHatcheries;
        }
    }

    /** The hold a lost expansion builder armed. */
    private static final class ExpansionBackoffInputs {
        private final int lostExpansionBuilders;
        private final int expansionHeldUntilFrame;

        private ExpansionBackoffInputs(int lostExpansionBuilders, int expansionHeldUntilFrame) {
            this.lostExpansionBuilders = lostExpansionBuilders;
            this.expansionHeldUntilFrame = expansionHeldUntilFrame;
        }
    }
}
