package telemetry;

import bwapi.Game;
import bwapi.Position;
import unit.squad.AirHarassEvaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes telemetry_harass.csv: one row per air harass event, and one ENTRY_CHECK row whenever the harass entry
 * verdict of an air squad changes.
 *
 * <p>ENTER and EXIT bound a harass episode; EXIT names its exit_reason. TICK rows carry the flock's position, size,
 * hit points and the anti-air it measured at its strike point, and contain_distance, the pixels from the flock to
 * the nearest held containment arc, or -1 with none held. KILL rows name the killed_type credited to the harass and
 * MUTA_LOST rows a Mutalisk lost while harassing. workers_killed, buildings_killed, other_killed and mutas_lost are
 * cumulative over the episode on every row that carries them.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class HarassLogger implements HarassSink {

    static final String FILE = "telemetry_harass.csv";

    static final String HEADER = "game_id,frame,squad_id,event,verdict,exit_reason,phase,base_x,base_y,strike_x,"
            + "strike_y,center_x,center_y,mutas,healthy_mutas,flock_hp,hp_loss_fraction,tolerance,air_defense,"
            + "avoided_zones,workers_killed,buildings_killed,other_killed,mutas_lost,killed_type,contain_distance,"
            + "bases_under_attack";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;
    private final Map<String, AirHarassEvaluator.EntryVerdict> lastVerdict = new HashMap<>();

    private boolean disabled;

    public HarassLogger(Game game, String gameId) {
        this(game, gameId, new TelemetryWriter(FILE, HEADER));
    }

    HarassLogger(Game game, String gameId, TelemetryWriter writer) {
        this.game = game;
        this.gameId = gameId;
        this.writer = writer;
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            if (game.getFrameCount() % FLUSH_INTERVAL_FRAMES == 0) {
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
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    /**
     * Records a row. An ENTRY_CHECK is written only when the squad's verdict differs from the last one written for
     * it, and an ENTER forgets that verdict, so the first check after a harass ends is written again.
     *
     * @param row the row
     */
    @Override
    public void onRow(HarassRow row) {
        if (disabled) {
            return;
        }

        try {
            if (row.getEvent() == HarassRow.Event.ENTRY_CHECK) {
                if (row.getVerdict() == lastVerdict.get(row.getSquadId())) {
                    return;
                }
                lastVerdict.put(row.getSquadId(), row.getVerdict());
            } else if (row.getEvent() == HarassRow.Event.ENTER) {
                lastVerdict.remove(row.getSquadId());
            }
            writer.append(row(gameId, row));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void disable() {
        disabled = true;
        lastVerdict.clear();
        HarassTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @param gameId game id
     * @param row the row to record
     * @return the row
     */
    static String row(String gameId, HarassRow row) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(row.getFrame()));
        fields.add(Csv.name(row.getSquadId()));
        fields.add(Csv.name(row.getEvent()));
        fields.add(Csv.name(row.getVerdict()));
        fields.add(Csv.name(row.getExitReason()));
        fields.add(Csv.name(row.getPhase()));
        fields.addAll(positionCells(row.getBase()));
        fields.addAll(positionCells(row.getStrikePoint()));
        fields.addAll(positionCells(row.getCenter()));
        fields.add(String.valueOf(row.getMutas()));
        fields.add(String.valueOf(row.getHealthyMutas()));
        fields.add(String.valueOf(row.getFlockHitPoints()));
        fields.add(Csv.format(row.getHpLossFraction()));
        fields.add(Csv.format(row.getTolerance()));
        fields.add(Csv.format(row.getAirDefense()));
        fields.add(String.valueOf(row.getAvoidedZones()));
        fields.add(String.valueOf(row.getWorkersKilled()));
        fields.add(String.valueOf(row.getBuildingsKilled()));
        fields.add(String.valueOf(row.getOtherKilled()));
        fields.add(String.valueOf(row.getMutasLost()));
        fields.add(Csv.name(row.getKilledType()));
        fields.add(Csv.format(row.getContainDistance()));
        fields.add(String.valueOf(row.getBasesUnderAttack()));
        return String.join(",", fields);
    }

    private static List<String> positionCells(Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getX()));
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getY()));
        return fields;
    }
}
