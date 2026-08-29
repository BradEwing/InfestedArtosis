package telemetry;

import bwapi.Game;
import bwapi.Player;
import bwapi.TilePosition;
import info.GameState;
import info.ResourceCount;
import learning.GameRecord;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.PlanType;
import strategy.buildorder.BuildOrder;
import util.Time;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * <p>Supply is reported halved. BWAPI counts supply in half units; build orders are named by real
 * supply, hence the supply_used_real and supply_total_real column names.
 *
 * <p>The bank and the available figures are both recorded because scheduling gates on available
 * resources, and only the pair distinguishes a broke bot from an over-reserved one.
 */
public class PlanEventLogger implements PlanEventSink {

    private static final int FLUSH_INTERVAL_FRAMES = 480;

    private static final String EVENT_ENQUEUE = "ENQUEUE";
    private static final String EVENT_TRANSITION = "TRANSITION";

    private static final String PLAN_HEADER = "frame,time,event,plan_id,plan_type,item,from_state,to_state,priority,"
            + "frames_in_state,age_frames,minerals,gas,available_minerals,available_gas,supply_used_real,"
            + "supply_total_real,larva,gatherers,queue_depth,plans_scheduled,plans_building,plans_morphing,"
            + "build_tile_x,build_tile_y,macro_hatchery,build_order";

    private static final String GAME_HEADER = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,"
            + "opponent_race,opener,build_order,detected_strategies,frame_count";

    private final Game game;
    private final GameState gameState;
    private final String openerName;
    private final int numStartingLocations;

    private final TelemetryWriter planWriter;
    private final TelemetryWriter gameWriter;

    private final Map<String, PlanTrace> traces = new HashMap<>();
    private final List<String> buffer = new ArrayList<>();

    private boolean disabled;
    private int currentFrame;
    private int lastFlushFrame;
    private int nextPlanId = 1;

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
            buffer.add(row(plan, trace, EVENT_ENQUEUE, null, plan.getState()));
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
            PlanTrace trace = traces.get(plan.getUuid());
            if (trace == null) {
                trace = newTrace(plan);
            }
            buffer.add(row(plan, trace, EVENT_TRANSITION, from, to));
            trace.setLastStateFrame(currentFrame);
        } catch (Exception e) {
            disabled = true;
        }
    }

    private PlanTrace newTrace(Plan plan) {
        PlanTrace trace = new PlanTrace(nextPlanId, currentFrame);
        nextPlanId += 1;
        traces.put(plan.getUuid(), trace);
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

    private String row(Plan plan, PlanTrace trace, String event, PlanState from, PlanState to) {
        Player self = gameState.getSelf();
        ResourceCount resourceCount = gameState.getResourceCount();
        TilePosition buildPosition = plan.getBuildPosition();

        StringBuilder sb = new StringBuilder();
        sb.append(currentFrame).append(',');
        sb.append(new Time(currentFrame)).append(',');
        sb.append(event).append(',');
        sb.append(trace.getId()).append(',');
        sb.append(planType(plan)).append(',');
        sb.append(plan.getName()).append(',');
        sb.append(from == null ? "" : from.toString()).append(',');
        sb.append(to == null ? "" : to.toString()).append(',');
        sb.append(plan.getPriority()).append(',');
        sb.append(currentFrame - trace.getLastStateFrame()).append(',');
        sb.append(currentFrame - trace.getEnqueueFrame()).append(',');
        sb.append(self.minerals()).append(',');
        sb.append(self.gas()).append(',');
        sb.append(resourceCount.availableMinerals()).append(',');
        sb.append(resourceCount.availableGas()).append(',');
        sb.append(self.supplyUsed() / 2).append(',');
        sb.append(self.supplyTotal() / 2).append(',');
        sb.append(gameState.numLarva()).append(',');
        sb.append(gameState.numGatherers()).append(',');
        sb.append(gameState.getProductionQueue().size()).append(',');
        sb.append(gameState.getPlansScheduled().size()).append(',');
        sb.append(gameState.getPlansBuilding().size()).append(',');
        sb.append(gameState.getPlansMorphing().size()).append(',');
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getX())).append(',');
        sb.append(buildPosition == null ? "" : String.valueOf(buildPosition.getY())).append(',');
        sb.append(plan.isMacroHatchery()).append(',');
        sb.append(activeBuildOrderName());
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
