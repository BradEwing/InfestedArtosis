package telemetry;

import bwapi.Game;
import bwapi.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes telemetry_harass.csv: one row per air harass event, and one ENTRY_CHECK row whenever the harass entry
 * verdict of an air squad changes.
 *
 * <p>ENTER and EXIT bound a harass episode; EXIT names its exit_reason. TICK rows carry the flock's position, size, hit
 * points and the anti-air it measured at its strike point, and contain_distance, the pixels from the flock to the
 * nearest held containment arc, or -1 with none held. ENTER, TICK and EXIT rows carry air_defense, the anti-air at the
 * strike point, and target_kind, BASE or EXPOSED; for an EXPOSED target base_x and base_y are -1 and the strike point
 * is the group's anchor. On an EXPOSED EXIT row air_defense is the group's exposure measure at the anchor it was last
 * followed to, the anti-air the STRIKE_DEFENDED exit compared against the tolerance, which leaves out a lone anti-air
 * member the flock kills quickly. TICK and EXIT rows carry flock_defense, the anti-air covering the flock's center,
 * which the FLOCK_DEFENDED exit compares against the tolerance. KILL rows name the killed_type credited to the harass
 * and MUTA_LOST rows a Mutalisk lost while harassing. workers_killed, buildings_killed, other_killed and mutas_lost are
 * cumulative over the episode on every row that carries them.
 *
 * <p>aa_sighting_age is the frames since the target base's core, and with it its anti-air, was last in sight; a base
 * never sighted reads the frame count. ENTRY_CHECK rows carry it for the chosen base, and ENTER, RETARGET and TICK
 * rows for the target base.
 *
 * <p>prober_hp, prober_peak_hp and prober_id are retired and always -1; they keep their columns so the columns after
 * them stay where they were.
 *
 * <p>aa_known_cover is 1 when known anti-air structures cover the chosen or target base's core point and 0 when they
 * do not, on ENTRY_CHECK, ENTER and RETARGET rows for a base.
 *
 * <p>An AA_REACTION row is written once for every harass the flock ends on newly seen anti-air it cannot answer, just
 * before the EXIT row whose reason is NEW_AA. aa_seen_frame is the frame the first of the anti-air making up that
 * defense came into sight, aa_turn_frame the frame the flock turned, and aa_hp_lost the flock's hit points lost
 * between the two, 0 when they are the same frame. aa_trigger_type and aa_trigger_id name the anti-air unit whose
 * sighting ended the harass, and aa_at_target is 1 when it stood within the zone of the target base and 0 when it
 * stood only at the flock.
 *
 * <p>edge_turrets is how many lone Missile Turrets the flock takes on instead of pricing them as a defense, see
 * AirHarassTargeting.edgeTurrets, on ENTRY_CHECK and TICK rows. An EDGE_TURRET row is written the first time a
 * Mutalisk of the harass attacks such a Turret, with its unit id in edge_turret_id. A UNIT_RETARGET row is written
 * when a Mutalisk leaves a target that is still alive for one of higher value, a Worker over a non-Worker or an edge
 * Turret over anything else: retarget_old_id and retarget_old_type name the
 * target it left, retarget_new_id and retarget_new_type the one it took.
 *
 * <p>stalled is 1 when the squad's FIGHT and RETREAT crossings read as a stall, see AirStallDetector, and 0 when they
 * do not, on ENTRY_CHECK and ENTER rows; it is -1 while the IA_AIR_FLAP_ESCAPE switch is off, which leaves the stall
 * detector unread. exposed_score is the best exposed
 * group's score and base_score the best base's, both in heat units and uncapped, so the two compare directly; a score
 * left at -1 had no group or base to score. ENTRY_CHECK, ENTER and RETARGET rows carry both.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class HarassLogger implements HarassSink {

    static final String FILE = "telemetry_harass.csv";

    static final String HEADER = "game_id,frame,squad_id,event,verdict,exit_reason,phase,base_x,base_y,strike_x,"
            + "strike_y,center_x,center_y,mutas,healthy_mutas,flock_hp,hp_loss_fraction,tolerance,air_defense,"
            + "avoided_zones,workers_killed,buildings_killed,other_killed,mutas_lost,killed_type,contain_distance,"
            + "bases_under_attack,target_kind,flock_defense,aa_sighting_age,prober_hp,prober_peak_hp,prober_id,"
            + "aa_known_cover,stalled,exposed_score,base_score,aa_seen_frame,aa_turn_frame,aa_hp_lost,"
            + "aa_trigger_type,aa_trigger_id,aa_at_target,edge_turrets,edge_turret_id,retarget_old_id,"
            + "retarget_old_type,retarget_new_id,retarget_new_type";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final String gameId;
    private final TelemetryWriter writer;
    private final Map<String, String> lastVerdict = new HashMap<>();

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
     * Records a row. An ENTRY_CHECK is written only when the squad's verdict or stalled cell differs from the last
     * one written for it, and an ENTER forgets that verdict, so the first check after a harass ends is written again.
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
                String check = row.getVerdict() + "/" + row.getStalled();
                if (check.equals(lastVerdict.get(row.getSquadId()))) {
                    return;
                }
                lastVerdict.put(row.getSquadId(), check);
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
        fields.add(Csv.name(row.getTargetKind()));
        fields.add(Csv.format(row.getFlockDefense()));
        fields.add(String.valueOf(row.getAaSightingAge()));
        fields.add(String.valueOf(NOT_EVALUATED));
        fields.add(String.valueOf(NOT_EVALUATED));
        fields.add(String.valueOf(NOT_EVALUATED));
        fields.add(String.valueOf(row.getAaKnownCover()));
        fields.add(String.valueOf(row.getStalled()));
        fields.add(Csv.format(row.getExposedScore()));
        fields.add(Csv.format(row.getBaseScore()));
        fields.add(String.valueOf(row.getAaSeenFrame()));
        fields.add(String.valueOf(row.getAaTurnFrame()));
        fields.add(String.valueOf(row.getAaHitPointsLost()));
        fields.add(Csv.name(row.getAaTriggerType()));
        fields.add(String.valueOf(row.getAaTriggerId()));
        fields.add(String.valueOf(row.getAaAtTarget()));
        fields.add(String.valueOf(row.getEdgeTurrets()));
        fields.add(String.valueOf(row.getEdgeTurretId()));
        fields.add(String.valueOf(row.getRetargetOldId()));
        fields.add(Csv.name(row.getRetargetOldType()));
        fields.add(String.valueOf(row.getRetargetNewId()));
        fields.add(Csv.name(row.getRetargetNewType()));
        return String.join(",", fields);
    }

    private static List<String> positionCells(Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getX()));
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getY()));
        return fields;
    }
}
