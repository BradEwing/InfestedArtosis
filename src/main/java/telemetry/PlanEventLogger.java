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
import macro.plan.BuilderLossReason;
import macro.plan.BuilderReading;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelSource;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.GasBoundHiveTech;
import strategy.buildorder.LarvaBoundMacroHatchery;
import unit.managed.ManagedUnit;
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
    private static final String EVENT_HIVE_TECH_TRIGGER = "HIVE_TECH_TRIGGER";
    private static final String EVENT_HIVE_TECH_WITHHELD = "HIVE_TECH_WITHHELD";
    private static final String EVENT_BUILDER_DISPATCH_DECISION = "BUILDER_DISPATCH_DECISION";
    private static final String EVENT_EXPANSION_BACKOFF = "EXPANSION_BACKOFF";
    private static final String EVENT_COLONY_BUILDER_BACKOFF = "COLONY_BUILDER_BACKOFF";
    private static final String EVENT_STRATEGY_DETECTED = "STRATEGY_DETECTED";
    private static final String EVENT_BASE_LOST = "BASE_LOST";
    private static final String EVENT_BUILDER_LOST = "BUILDER_LOST";
    private static final String EVENT_BUILDER_REDISPATCH = "BUILDER_REDISPATCH";

    private static final int NO_STARVED_COUNT = -1;

    private static final String EVENT_RECURRING_CANCEL = "RECURRING_CANCEL";

    /**
     * 66 columns; readers that index by position rather than by name must match this order.
     * enemy_air, gas_gathered, enemy_barracks, the blocker mineral pair, the enemy ground pair,
     * yield_to_plan_id, the four macro hatchery gate columns and the four Hive tech gate columns
     * are trailing columns written by {@link #appendTrailing}, so every row shape keeps one width.
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
     * tech_gate, gate_available_gas, gate_required_gas and extractors_completed are set only on
     * HIVE_TECH_TRIGGER and HIVE_TECH_WITHHELD rows: the gate the Hive-branch request stopped on,
     * the unreserved gas it read, the branch bar it measured against, and the finished Extractors
     * at that frame. The extractor count is a diagnostic; no gate reads it.
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
     * builder_route_enemies, builder_site_enemies, builder_route_defense_zones, builder_at_site,
     * builder_site_at_our_base and builder_at_our_base are what a builder would walk into and
     * whether either end of the walk is ground we hold, written on every BUILDING row that has an
     * executor and read fresh on the row's own frame. builder_dispatch_decision is what the gate
     * did with it, set on BUILDER_DISPATCH_DECISION rows and, as a builder's loss, on BUILDER_LOST
     * and BUILDER_REDISPATCH rows: a dispatch a threat reading would otherwise have held reads
     * DISPATCH_HOME_SITE, and carries both ownership columns true.
     * <p>
     * builder_at_site is a base-region test, not arrival: whether the builder stands on the site's
     * base tiles, which are the whole main for a site in the main and every tile within a manhattan
     * radius of the site elsewhere ({@link GameState#siteTiles}). For a site in the main, a builder
     * anywhere in the main reads true however far it is from the site. builder_in_range is arrival:
     * the builder's distance to the centre of the building's footprint is within the distance at
     * which it issues the morph, measured as the builder measures it.
     * <p>
     * builder_role, builder_order and builder_in_range describe the plan's executor: its UnitRole,
     * its BWAPI order and whether it is in build range. They are set on BUILD_AHEAD_HOLD,
     * BUILD_AHEAD_EVICT, BUILDER_DISPATCH_DECISION, BUILDER_LOST and BUILDER_REDISPATCH rows.
     * builder_role reads NONE on those rows when the plan has no executor and UNMANAGED when its
     * executor is not a managed unit; builder_in_range is blank unless the executor is a drone
     * building a structure on a known tile.
     * <p>
     * BUILDER_LOST is written when a walking builder stops executing its BUILDING plan for any reason
     * but a threat recall. It reports the lost builder as last read: executor_unit_id,
     * builder_distance_px and the builder columns are that builder's, and builder_dispatch_decision
     * is the reason, LOST_ROLE_CHANGED, LOST_PLAN_UNBOUND, LOST_STRAYED or LOST_DIED. A builder
     * killed on its walk is reported as read on the frame before it died, and its plan is cancelled.
     * BUILDER_REDISPATCH is written when a new builder is dispatched for a plan that lost one:
     * executor_unit_id and the builder columns are the new builder's, previous_executor_unit_id is
     * the lost builder's, and builder_dispatch_decision repeats the reason it was lost. A
     * previous_executor_unit_id equal to executor_unit_id is the same drone dispatched again.
     * <p>
     * lost_expansion_builders and expansion_hold_until_frame are set only on EXPANSION_BACKOFF
     * rows. The hold a row armed is expansion_hold_until_frame minus frame.
     * <p>
     * COLONY_BUILDER_BACKOFF rows reuse the same two columns for a hold on sunken planning at one
     * base: lost_expansion_builders is the colony builders lost at that base since a colony there
     * last started morphing, and expansion_hold_until_frame is the frame the base's hold lifts. The
     * held base's location is in build_tile_x and build_tile_y.
     * <p>
     * STRATEGY_DETECTED rows carry the detected strategy's detection label in item and leave every
     * plan column empty, so the frame a strategy was detected is the row's frame. The label is the
     * strategy's name, followed for ProxyGate by the evidence arms that fired: ProxyGate:GATEWAY_AWAY,
     * ProxyGate:MAIN_EMPTY or ProxyGate:GATEWAY_AWAY+MAIN_EMPTY.
     * <p>
     * base_inner is set only on BASE_LOST rows, written when one of our bases loses its hatchery:
     * true for the main or a natural, false for a third or later base. The lost base's location is
     * in build_tile_x and build_tile_y.
     */
    static final String PLAN_HEADER = "frame,time,event,plan_id,executor_unit_id,plan_type,item,from_state,"
            + "to_state,cancel_reason,cancel_source,blocker,blocked_frames,priority,frames_in_state,age_frames,"
            + "minerals,gas,available_minerals,available_gas,supply_used_real,supply_total_real,larva,assigned_larva,"
            + "reserved_larva,gatherers,queue_depth,plans_scheduled,plans_building,plans_morphing,build_tile_x,"
            + "build_tile_y,macro_hatchery,build_order,starved_behind,builder_distance_px,enemy_air,gas_gathered,"
            + "enemy_barracks,blocker_mineral_x,blocker_mineral_y,enemy_ground_known_at_bases,"
            + "enemy_ground_visible_at_bases,yield_to_plan_id,macro_hatchery_gate,hatcheries,macro_tech_ready,"
            + "macro_hatcheries_outstanding,tech_gate,gate_available_gas,gate_required_gas,"
            + "extractors_completed,builder_route_enemies,builder_site_enemies,"
            + "builder_route_defense_zones,builder_at_site,builder_dispatch_decision,lost_expansion_builders,"
            + "expansion_hold_until_frame,builder_site_at_our_base,builder_at_our_base,base_inner,"
            + "builder_role,builder_order,builder_in_range,previous_executor_unit_id";

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

    private boolean lastMacroHatcheryStarved;

    private final Map<String, GasBoundHiveTech.Gate> lastHiveTechGates = new HashMap<>();

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
            newTrace(plan);
            buffer.add(row(plan, EVENT_ENQUEUE, null, plan.getState(), PlanBlocker.NONE, 0, NO_STARVED_COUNT));
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
            buffer.add(row(plan, EVENT_TRANSITION, from, to, PlanBlocker.NONE, 0, NO_STARVED_COUNT));
            trace.setLastStateFrame(currentFrame);
            trace.clearStaleReported();
            if (to == PlanState.COMPLETE || to == PlanState.CANCELLED) {
                openPlans.remove(plan.getUuid());
            }
            if (to == PlanState.CANCELLED && plan.getCancelSource() != null && recordRecurrence(plan)) {
                buffer.add(row(plan, EVENT_RECURRING_CANCEL, from, to, PlanBlocker.NONE, 0, NO_STARVED_COUNT));
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
            buffer.add(row(plan, EVENT_STALE, null, plan.getState(), blocker, waited, NO_STARVED_COUNT));
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
            StringBuilder sb = planColumns(holder, EVENT_BUILD_AHEAD_YIELD, null, holder.getState(),
                    PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, heldFrames, NO_STARVED_COUNT, executorReading(holder));
            appendTrailing(sb, null, emergency, null, null, builderThreat(holder), null, BuilderColumns.BLANK);
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
            StringBuilder sb = planColumns(plan, EVENT_BLOCKER_DIVERT, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT, executorReading(plan));
            appendTrailing(sb, mineral, null, null, null, builderThreat(plan), null, BuilderColumns.BLANK);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row each time the larva-bound macro hatchery request reaches a new gate, or the
     * build enters or leaves larva starvation, while the build is larva bound and floating both
     * banks. A request that stops on one gate for many frames in one state writes one row, on the
     * frame it reached that state.
     */
    @Override
    public void onMacroHatcheryGate(LarvaBoundMacroHatchery.Gate gate, boolean techReady, int hatcheries,
                                    int outstandingMacroHatcheries) {
        if (disabled) {
            return;
        }

        try {
            boolean starved = larvaStarved();
            if (!isNewMacroHatcheryGateReading(gate, starved, lastMacroHatcheryGate, lastMacroHatcheryStarved)) {
                return;
            }
            lastMacroHatcheryGate = gate;
            lastMacroHatcheryStarved = starved;
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
     * Whether a gate reading is a row rather than a repeat of the one before it.
     *
     * <p>Keyed on the gate and on larva starvation together. A run of starved frames that begins
     * under a gate that was already standing is a new reading, so the run carries a row at the
     * frame it began rather than only the row written before it.
     *
     * @param gate the gate this frame's request stopped on
     * @param starved whether the build is larva starved with both banks floating and no threat
     * @param lastGate the gate the previous reading stopped on, or null before the first
     * @param lastStarved the starvation state of the previous reading
     * @return true when the reading should be written
     */
    static boolean isNewMacroHatcheryGateReading(LarvaBoundMacroHatchery.Gate gate, boolean starved,
                                                 LarvaBoundMacroHatchery.Gate lastGate, boolean lastStarved) {
        return gate != lastGate || starved != lastStarved;
    }

    /**
     * The state a larva starvation run is measured over: no free larva, both unreserved banks at
     * or above the request's own float bars, and no enemy ground unit known at our bases.
     *
     * <p>Half of the de-duplication key, because the gate alone is not enough. A request that
     * stops on the same gate either side of the frame this turns true - a build still waiting on
     * its tech, or one whose macro hatchery is already outstanding - would otherwise write its row
     * before the run began and nothing inside it, leaving the run the row exists to witness
     * unmarked for as long as the answer did not change.
     *
     * @return true while the build is larva starved with both banks floating and no threat
     */
    private boolean larvaStarved() {
        ResourceCount resourceCount = gameState.getResourceCount();
        return gameState.numLarva() == 0
                && resourceCount.availableMinerals() >= LarvaBoundMacroHatchery.FLOAT_MINERALS
                && resourceCount.availableGas() >= LarvaBoundMacroHatchery.FLOAT_GAS
                && gameState.knownEnemyMobileGroundCombatUnitsAtOurBases() == 0;
    }

    /**
     * Writes one row each time a Hive-branch structure reaches a new gate while the build could
     * plan it. A structure sitting on one gate for many frames writes one row, on the frame it
     * reached that gate, so an absence carries a reason without carrying a row per frame.
     */
    @Override
    public void onHiveTechGate(GasBoundHiveTech.Gate gate, UnitType structure, int availableGas,
                               int requiredGas, int extractorsCompleted) {
        if (disabled) {
            return;
        }

        try {
            String item = structure.toString();
            if (gate == lastHiveTechGates.get(item)) {
                return;
            }
            lastHiveTechGates.put(item, gate);
            if (!gate.isRequest()) {
                return;
            }
            buffer.add(hiveTechGateRow(new HiveTechGateInputs(gate, item, availableGas, requiredGas,
                    extractorsCompleted)));
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
            BuilderColumns builder = BuilderColumns.gateDecision(executorReading(plan), decision);
            StringBuilder sb = planColumns(plan, EVENT_BUILDER_DISPATCH_DECISION, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT, builder.executor());
            appendTrailing(sb, null, null, null, null, threat, null, builder);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row per builder lost, with the builder as it was last read rather than the plan's
     * executor now, which the release has already cleared. The row carries no builder threat
     * reading, since a killed builder has no tile to read it from.
     */
    @Override
    public void onBuilderLost(Plan plan, BuilderLossReason reason, BuilderReading builder) {
        if (disabled) {
            return;
        }

        try {
            BuilderColumns columns = BuilderColumns.lost(reason, builder);
            StringBuilder sb = planColumns(plan, EVENT_BUILDER_LOST, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT, columns.executor());
            appendTrailing(sb, null, null, null, null, null, null, columns);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /** Writes one row per new builder dispatched for a plan that lost its builder. */
    @Override
    public void onBuilderRedispatch(Plan plan, BuilderLossReason reason, BuilderReading lost, BuilderReading taker) {
        if (disabled) {
            return;
        }

        try {
            BuilderColumns columns = BuilderColumns.redispatch(reason, lost, taker);
            StringBuilder sb = planColumns(plan, EVENT_BUILDER_REDISPATCH, null, plan.getState(),
                    PlanBlocker.NONE, 0, NO_STARVED_COUNT, columns.executor());
            appendTrailing(sb, null, null, null, null, builderThreat(plan), null, columns);
            buffer.add(sb.toString());
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row per hold armed, with no plan behind it: the plan that armed it is already
     * cancelled.
     *
     * <p>The frame is re-read rather than taken from the last onFrame. A hold is almost always
     * armed from onUnitDestroy, which JBWAPI dispatches ahead of the frame's onFrame, so the
     * cached frame is one behind the frame the hold was armed on and the window a reader derives
     * from expansion_hold_until_frame minus frame would read one frame too long.
     */
    @Override
    public void onExpansionBackoff(int lostExpansionBuilders, int expansionHeldUntilFrame) {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            buffer.add(expansionBackoffRow(BaseEventInputs.expansionBackoff(lostExpansionBuilders,
                    expansionHeldUntilFrame)));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Writes one row per colony hold armed. The frame is re-read for the reason
     * {@link #onExpansionBackoff} gives: the builder is lost from onUnitDestroy.
     */
    @Override
    public void onColonyBuilderBackoff(TilePosition base, int lostColonyBuilders, int colonyHeldUntilFrame) {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            buffer.add(colonyBuilderBackoffRow(base, BaseEventInputs.expansionBackoff(lostColonyBuilders,
                    colonyHeldUntilFrame)));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * Records the frame a strategy was detected. The frame is re-read rather than taken from the last
     * onFrame, because StrategyTracker may run ahead of this logger's onFrame on the same frame.
     */
    @Override
    public void onStrategyDetected(String detectionLabel) {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            buffer.add(strategyDetectedRow(detectionLabel));
        } catch (Exception e) {
            disabled = true;
        }
    }

    /**
     * The frame is re-read rather than taken from the last onFrame, since a base is lost from
     * onUnitDestroy, which JBWAPI dispatches ahead of the frame's onFrame.
     */
    @Override
    public void onBaseLost(TilePosition base, boolean innerBase) {
        if (disabled) {
            return;
        }

        try {
            currentFrame = game.getFrameCount();
            buffer.add(baseLostRow(base, innerBase));
        } catch (Exception e) {
            disabled = true;
        }
    }

    private void buildAheadRow(String event, Plan holder, int heldFrames, int starvedBehind) {
        if (disabled) {
            return;
        }

        try {
            BuilderColumns builder = BuilderColumns.current(executorReading(holder));
            StringBuilder sb = planColumns(holder, event, null, holder.getState(),
                    PlanBlocker.BUILD_AHEAD_SLOT_TAKEN, heldFrames, starvedBehind, builder.executor());
            appendTrailing(sb, null, null, null, null, builderThreat(holder), null, builder);
            buffer.add(sb.toString());
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
        buffer.add(row(plan, EVENT_BLOCKED, null, null, blocker, waited, NO_STARVED_COUNT));
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
            buffer.add(row(plan, EVENT_OPEN_AT_GAME_END, null, plan.getState(), PlanBlocker.NONE, 0, NO_STARVED_COUNT));
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

    private String row(Plan plan, String event, PlanState from, PlanState to,
                       PlanBlocker blocker, int blockedFrames, int starvedBehind) {
        StringBuilder sb = planColumns(plan, event, from, to, blocker, blockedFrames, starvedBehind,
                executorReading(plan));
        appendTrailing(sb, null, null, null, null, builderThreat(plan), null, BuilderColumns.BLANK);
        return sb.toString();
    }

    /**
     * Every plan-row column up to and including builder_distance_px, with executor_unit_id and
     * builder_distance_px taken from the given reading. builder_distance_px is blank until both the
     * executor and the build position are known, which is what separates a builder still walking
     * from one that arrived and could not place.
     */
    private StringBuilder planColumns(Plan plan, String event, PlanState from, PlanState to, PlanBlocker blocker,
                                      int blockedFrames, int starvedBehind, BuilderReading executor) {
        PlanTrace trace = trace(plan);
        TilePosition buildPosition = plan.getBuildPosition();
        boolean cancelled = to == PlanState.CANCELLED;
        PlanCancelSource cancelSource = plan.getCancelSource();
        BuilderColumns executorColumns = BuilderColumns.current(executor);

        StringBuilder sb = new StringBuilder();
        appendEvent(sb, event);
        sb.append(plan.getPlanId()).append(',');
        sb.append(executorColumns.executorUnitId()).append(',');
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
        sb.append(executorColumns.distance()).append(',');
        return sb;
    }

    /** The plan's executor read now, or null when the plan has none. */
    private BuilderReading executorReading(Plan plan) {
        Unit executor = gameState.executorOf(plan);
        if (executor == null) {
            return null;
        }
        ManagedUnit managedUnit = gameState.getManagedUnitLookup().get(executor);
        return BuilderReading.of(executor, managedUnit == null ? null : managedUnit.getRole(), plan);
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
        appendTrailing(sb, null, null, null, null, null, null, BuilderColumns.BLANK);
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
        appendTrailing(sb, null, null, inputs, null, null, null, BuilderColumns.BLANK);
        return sb.toString();
    }

    /** A row for a Hive-branch request, which has no plan behind it, so the plan columns are empty. */
    private String hiveTechGateRow(HiveTechGateInputs inputs) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, inputs.gate == GasBoundHiveTech.Gate.TRIGGER
                ? EVENT_HIVE_TECH_TRIGGER : EVENT_HIVE_TECH_WITHHELD);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(inputs.item)).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 2);
        sb.append(false).append(',');
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, inputs, null, null, BuilderColumns.BLANK);
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
        appendTrailing(sb, null, null, null, null, null, null, BuilderColumns.BLANK);
        return sb.toString();
    }

    /** A row for a hold on expanding, which no plan owns, so the plan columns are empty. */
    private String expansionBackoffRow(BaseEventInputs inputs) {
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
        appendTrailing(sb, null, null, null, null, null, inputs, BuilderColumns.BLANK);
        return sb.toString();
    }

    /** A row for a hold on sunken planning at one base, which no plan owns, so the plan columns are empty. */
    private String colonyBuilderBackoffRow(TilePosition base, BaseEventInputs inputs) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_COLONY_BUILDER_BACKOFF);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(UnitType.Zerg_Creep_Colony.toString())).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        sb.append(base.getX()).append(',');
        sb.append(base.getY()).append(',');
        appendEmpty(sb, 1);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, null, null, inputs, BuilderColumns.BLANK);
        return sb.toString();
    }

    /** A row for a detected strategy, which no plan owns, so the plan columns are empty. */
    private String strategyDetectedRow(String detectionLabel) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_STRATEGY_DETECTED);
        appendEmpty(sb, 3);
        sb.append(Csv.sanitize(detectionLabel)).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        appendEmpty(sb, 3);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, null, null, null, BuilderColumns.BLANK);
        return sb.toString();
    }

    /** A row for a base that lost its hatchery, which no plan owns, so the plan columns are empty. */
    private String baseLostRow(TilePosition base, boolean innerBase) {
        StringBuilder sb = new StringBuilder();
        appendEvent(sb, EVENT_BASE_LOST);
        appendEmpty(sb, 2);
        sb.append(PlanType.BUILDING).append(',');
        sb.append(Csv.sanitize(UnitType.Zerg_Hatchery.toString())).append(',');
        appendEmpty(sb, 4);
        appendBlocker(sb, PlanBlocker.NONE, 0);
        appendEmpty(sb, 3);
        appendGameState(sb);
        sb.append(base.getX()).append(',');
        sb.append(base.getY()).append(',');
        appendEmpty(sb, 1);
        sb.append(Csv.sanitize(activeBuildOrderName())).append(',');
        appendEmpty(sb, 2);
        appendTrailing(sb, null, null, null, null, null, BaseEventInputs.baseLost(innerBase),
                BuilderColumns.BLANK);
        return sb.toString();
    }

    /**
     * What the builder for this plan would walk into, or null when the plan has no builder to read
     * it for. Recomputed per row rather than carried from the last gate evaluation, so a row taken
     * between two evaluations reports the frame it was written on.
     *
     * <p>A Lair, Hive or colony morph is a building plan with an executor too, but its executor is
     * the structure morphing in place. It walks nowhere, so its route terms are zero by fact
     * rather than by omission, and only the site reading says anything about it.
     */
    private BuilderThreat builderThreat(Plan plan) {
        if (plan.getType() != PlanType.BUILDING) {
            return null;
        }
        Unit executor = gameState.executorOf(plan);
        if (executor == null) {
            return null;
        }
        if (!executor.getType().isWorker()) {
            return gameState.siteThreat(plan.getBuildPosition(), executor.getTilePosition());
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
     * @param hiveTech the Hive-branch request's gate and inputs, or null on every row but
     *     HIVE_TECH_TRIGGER and HIVE_TECH_WITHHELD
     * @param builderThreat what the plan's builder would walk into, or null when the row has no
     *     BUILDING plan with an executor behind it
     * @param baseEvent the expansion or colony hold armed or the base lost, or null on every row but
     *     EXPANSION_BACKOFF, COLONY_BUILDER_BACKOFF and BASE_LOST
     * @param builder the builder columns, {@link BuilderColumns#BLANK} on every row but
     *     BUILD_AHEAD_HOLD, BUILD_AHEAD_EVICT, BUILDER_DISPATCH_DECISION, BUILDER_LOST and
     *     BUILDER_REDISPATCH, and the carrier of builder_dispatch_decision
     */
    private void appendTrailing(StringBuilder sb, Position blockerMineral, Plan yieldTo,
                                MacroHatcheryGateInputs macroHatchery, HiveTechGateInputs hiveTech,
                                BuilderThreat builderThreat, BaseEventInputs baseEvent, BuilderColumns builder) {
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
        sb.append(hiveTech == null ? "" : hiveTech.gate.toString()).append(',');
        sb.append(hiveTech == null ? "" : String.valueOf(hiveTech.availableGas)).append(',');
        sb.append(hiveTech == null ? "" : String.valueOf(hiveTech.requiredGas)).append(',');
        sb.append(hiveTech == null ? "" : String.valueOf(hiveTech.extractorsCompleted)).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getRouteEnemies())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getSiteEnemies())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.getRouteDefenseZones())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.isBuilderAtSite())).append(',');
        sb.append(orEmpty(builder.decision())).append(',');
        sb.append(baseEvent == null ? "" : orEmpty(baseEvent.lostExpansionBuilders)).append(',');
        sb.append(baseEvent == null ? "" : orEmpty(baseEvent.expansionHeldUntilFrame)).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.isSiteAtOurBase())).append(',');
        sb.append(builderThreat == null ? "" : String.valueOf(builderThreat.isBuilderAtOurBase())).append(',');
        sb.append(baseEvent == null ? "" : orEmpty(baseEvent.baseInner));
        for (String cell : builder.trailing()) {
            sb.append(',').append(cell);
        }
    }

    private static String orEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    /** The gate a Hive-branch request stopped on and the inputs no other row column carries. */
    private static final class HiveTechGateInputs {
        private final GasBoundHiveTech.Gate gate;
        private final String item;
        private final int availableGas;
        private final int requiredGas;
        private final int extractorsCompleted;

        private HiveTechGateInputs(GasBoundHiveTech.Gate gate, String item, int availableGas, int requiredGas,
                                   int extractorsCompleted) {
            this.gate = gate;
            this.item = item;
            this.availableGas = availableGas;
            this.requiredGas = requiredGas;
            this.extractorsCompleted = extractorsCompleted;
        }
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

    /** The hold a lost expansion builder armed, or whether a lost base was the main or a natural. */
    private static final class BaseEventInputs {
        private final Integer lostExpansionBuilders;
        private final Integer expansionHeldUntilFrame;
        private final Boolean baseInner;

        private BaseEventInputs(Integer lostExpansionBuilders, Integer expansionHeldUntilFrame, Boolean baseInner) {
            this.lostExpansionBuilders = lostExpansionBuilders;
            this.expansionHeldUntilFrame = expansionHeldUntilFrame;
            this.baseInner = baseInner;
        }

        private static BaseEventInputs expansionBackoff(int lostExpansionBuilders, int expansionHeldUntilFrame) {
            return new BaseEventInputs(lostExpansionBuilders, expansionHeldUntilFrame, null);
        }

        private static BaseEventInputs baseLost(boolean baseInner) {
            return new BaseEventInputs(null, null, baseInner);
        }
    }
}
