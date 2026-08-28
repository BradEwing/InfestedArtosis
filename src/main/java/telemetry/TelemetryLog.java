package telemetry;

import java.nio.file.Path;

/**
 * Owns the three telemetry CSV files.
 *
 * Every file name carries the telemetry_ prefix so the batch harness can exclude them from its
 * learning-row glob. When disabled this allocates no writer and opens no file handle.
 */
public final class TelemetryLog {

    static final String GAME_FILE = "telemetry_combat_game.csv";
    static final String ENGAGEMENT_FILE = "telemetry_engagements.csv";
    static final String ENGAGEMENT_UNIT_FILE = "telemetry_engagement_units.csv";

    static final String GAME_HEADER = "game_id,map_name,opponent_name,opponent_race,build_order,is_winner,end_frame,"
            + "average_fps,engagement_count,our_units_lost,our_supply_lost,enemy_units_killed,enemy_supply_killed,"
            + "sample_interval_frames,contact_radius_px,merge_radius_px,close_cooldown_frames";

    static final String ENGAGEMENT_HEADER = "game_id,engagement_id,start_frame,end_frame,anchor_x,anchor_y,"
            + "our_supply_open,our_supply_peak,our_supply_close,army_supply_open,our_units_lost,our_supply_lost,"
            + "arrival_p25_offset,arrival_p50_offset,arrival_p75_offset,enemy_supply_seen_peak,enemy_units_killed,"
            + "enemy_supply_killed,kills_ambiguous,squads_involved,squad_status_open,squad_status_close,"
            + "status_change_count,sim_ratio_open,sim_engage_threshold_open,sim_verdict_open";

    static final String ENGAGEMENT_UNIT_HEADER = "game_id,engagement_id,unit_id,unit_type,supply,arrival_frame,"
            + "arrival_offset,exit_frame,died,death_frame,death_x,death_y,hp_at_arrival,hp_at_exit,role_at_arrival,"
            + "squad_at_arrival";

    private final boolean enabled;
    private final TelemetryWriter gameWriter;
    private final TelemetryWriter engagementWriter;
    private final TelemetryWriter engagementUnitWriter;

    public TelemetryLog(boolean enabled, Path writeDirectory) {
        this.enabled = enabled;
        this.gameWriter = enabled ? new TelemetryWriter(writeDirectory.resolve(GAME_FILE), GAME_HEADER) : null;
        this.engagementWriter = enabled
                ? new TelemetryWriter(writeDirectory.resolve(ENGAGEMENT_FILE), ENGAGEMENT_HEADER) : null;
        this.engagementUnitWriter = enabled
                ? new TelemetryWriter(writeDirectory.resolve(ENGAGEMENT_UNIT_FILE), ENGAGEMENT_UNIT_HEADER) : null;
    }

    public void appendGame(String row) {
        if (!enabled) {
            return;
        }
        gameWriter.append(row);
    }

    public void appendEngagement(String row) {
        if (!enabled) {
            return;
        }
        engagementWriter.append(row);
    }

    public void appendEngagementUnit(String row) {
        if (!enabled) {
            return;
        }
        engagementUnitWriter.append(row);
    }

    public void flush() {
        if (!enabled) {
            return;
        }
        gameWriter.flush();
        engagementWriter.flush();
        engagementUnitWriter.flush();
    }
}
