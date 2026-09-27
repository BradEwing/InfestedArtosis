package telemetry;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.AirReinforcement;
import unit.squad.Squad;
import unit.squad.SquadManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes telemetry_air_reinforcement.csv.
 *
 * <p>HATCH is written the first frame a completed Mutalisk is seen, FIRST_ATTACK the first frame its weapon is on
 * cooldown or it starts an attack, carrying hatch_frame, and DEATH the first frame it is gone, carrying the squad it
 * was in and nearest_mate_px, the pixels to the nearest other Mutalisk on the frame before, -1 with none alive. A
 * Mutalisk that morphs into a Guardian or Devourer writes no DEATH. ACTIVE_COUNT is written whenever the number of
 * fighting, retreating or harassing air squads changes, in active_air_squads.
 *
 * <p>ROUTE is written when a rallying air squad starts flying to an active air squad, or changes target, with the
 * waypoints and path_px of its path; REFUSED when no active air squad has a safe path, once per refusal episode; and
 * JOIN when it arrives and hands its members over.
 *
 * <p>Constructed only when combat telemetry is enabled.
 */
public class AirReinforcementLogger implements AirReinforcementSink {

    static final String FILE = "telemetry_air_reinforcement.csv";

    static final String HEADER = "game_id,frame,event,unit_id,squad_id,squad_status,target_squad_id,target_status,"
            + "center_x,center_y,target_x,target_y,mutas,waypoints,path_px,nearest_mate_px,active_air_squads,"
            + "hatch_frame";

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final int NOT_EVALUATED = -1;

    private final Game game;
    private final SquadManager squadManager;
    private final String gameId;
    private final TelemetryWriter writer;

    private final Map<Integer, Integer> hatchFrames = new HashMap<>();
    private final Set<Integer> attacked = new HashSet<>();
    private final Set<String> refused = new HashSet<>();
    private Map<Integer, Position> lastPositions = new HashMap<>();
    private Map<Integer, Squad> lastSquads = new HashMap<>();
    private int lastActive = NOT_EVALUATED;

    private boolean disabled;

    public AirReinforcementLogger(Game game, SquadManager squadManager, String gameId) {
        this(game, squadManager, gameId, new TelemetryWriter(FILE, HEADER));
    }

    AirReinforcementLogger(Game game, SquadManager squadManager, String gameId, TelemetryWriter writer) {
        this.game = game;
        this.squadManager = squadManager;
        this.gameId = gameId;
        this.writer = writer;
    }

    public void onFrame() {
        if (disabled) {
            return;
        }

        try {
            int frame = game.getFrameCount();
            trackMutalisks(frame);
            trackActiveSquads(frame);
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
            writer.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    /**
     * Records a row. A REFUSED row is written only for the first refusal since the squad was last routed, or since
     * it was first evaluated.
     *
     * @param row the row
     */
    @Override
    public void onRow(AirReinforcementRow row) {
        if (disabled) {
            return;
        }

        try {
            if (row.getEvent() == AirReinforcementRow.Event.REFUSED) {
                if (!refused.add(row.getSquadId())) {
                    return;
                }
            } else if (row.getSquadId() != null) {
                refused.remove(row.getSquadId());
            }
            writer.append(row(gameId, row));
        } catch (RuntimeException e) {
            disable();
        }
    }

    private void trackMutalisks(int frame) {
        Map<Integer, Position> alive = new HashMap<>();
        for (Unit unit : game.self().getUnits()) {
            if (unit.getType() != UnitType.Zerg_Mutalisk || !unit.isCompleted()) {
                continue;
            }
            int id = unit.getID();
            alive.put(id, unit.getPosition());
            if (!hatchFrames.containsKey(id)) {
                hatchFrames.put(id, frame);
                onRow(AirReinforcementRow.builder().frame(frame).event(AirReinforcementRow.Event.HATCH).unitId(id)
                        .center(unit.getPosition()).build());
            }
            if (!attacked.contains(id) && attacking(unit)) {
                attacked.add(id);
                onRow(AirReinforcementRow.builder().frame(frame).event(AirReinforcementRow.Event.FIRST_ATTACK)
                        .unitId(id).center(unit.getPosition()).hatchFrame(hatchFrames.get(id)).build());
            }
        }
        for (Map.Entry<Integer, Position> entry : lastPositions.entrySet()) {
            int id = entry.getKey();
            if (alive.containsKey(id)) {
                continue;
            }
            Unit unit = game.getUnit(id);
            if (unit != null && unit.exists()) {
                continue;
            }
            Squad squad = lastSquads.get(id);
            onRow(AirReinforcementRow.builder().frame(frame).event(AirReinforcementRow.Event.DEATH).unitId(id)
                    .squadId(squad == null ? null : squad.getId())
                    .squadStatus(squad == null ? null : squad.getStatus())
                    .center(entry.getValue())
                    .nearestMateDistance(nearestMateDistance(id, lastPositions))
                    .hatchFrame(hatchFrames.getOrDefault(id, NOT_EVALUATED))
                    .build());
        }
        lastPositions = alive;
        lastSquads = alive.isEmpty() ? new HashMap<>() : squadsOf(squadManager.fightSquads);
    }

    private void trackActiveSquads(int frame) {
        int active = activeAirSquads(squadManager.fightSquads);
        if (active == lastActive) {
            return;
        }
        lastActive = active;
        onRow(AirReinforcementRow.builder().frame(frame).event(AirReinforcementRow.Event.ACTIVE_COUNT)
                .activeAirSquads(active).build());
    }

    private static boolean attacking(Unit unit) {
        return unit.getAirWeaponCooldown() > 0 || unit.getGroundWeaponCooldown() > 0 || unit.isStartingAttack();
    }

    private static Map<Integer, Squad> squadsOf(Collection<Squad> squads) {
        Map<Integer, Squad> byUnit = new HashMap<>();
        for (Squad squad : squads) {
            for (ManagedUnit member : squad.getMembers()) {
                byUnit.put(member.getUnitID(), squad);
            }
        }
        return byUnit;
    }

    /**
     * Air squads holding members and an active status, see {@link AirReinforcement#isActive}.
     *
     * @param squads fight squads
     * @return the count
     */
    static int activeAirSquads(Collection<Squad> squads) {
        int active = 0;
        for (Squad squad : squads) {
            if (squad.isAirSquad() && squad.size() > 0 && AirReinforcement.isActive(squad.getStatus())) {
                active++;
            }
        }
        return active;
    }

    /**
     * Pixels from a Mutalisk to the nearest other Mutalisk.
     *
     * @param id the Mutalisk's unit id
     * @param positions positions of every Mutalisk by unit id, its own included
     * @return the distance, or -1 with no other Mutalisk or no position for it
     */
    static double nearestMateDistance(int id, Map<Integer, Position> positions) {
        Position own = positions.get(id);
        if (own == null) {
            return NOT_EVALUATED;
        }
        double nearest = NOT_EVALUATED;
        for (Map.Entry<Integer, Position> entry : positions.entrySet()) {
            if (entry.getKey() == id) {
                continue;
            }
            double distance = own.getDistance(entry.getValue());
            if (nearest < 0 || distance < nearest) {
                nearest = distance;
            }
        }
        return nearest;
    }

    private void disable() {
        disabled = true;
        refused.clear();
        AirReinforcementTelemetry.clear();
    }

    /**
     * Builds one CSV row in {@link #HEADER} order.
     *
     * @param gameId game id
     * @param row the row to record
     * @return the row
     */
    static String row(String gameId, AirReinforcementRow row) {
        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(row.getFrame()));
        fields.add(Csv.name(row.getEvent()));
        fields.add(String.valueOf(row.getUnitId()));
        fields.add(Csv.name(row.getSquadId()));
        fields.add(Csv.name(row.getSquadStatus()));
        fields.add(Csv.name(row.getTargetSquadId()));
        fields.add(Csv.name(row.getTargetStatus()));
        fields.addAll(positionCells(row.getCenter()));
        fields.addAll(positionCells(row.getTargetCenter()));
        fields.add(String.valueOf(row.getMutas()));
        fields.add(String.valueOf(row.getWaypoints()));
        fields.add(Csv.format(row.getPathLength()));
        fields.add(Csv.format(row.getNearestMateDistance()));
        fields.add(String.valueOf(row.getActiveAirSquads()));
        fields.add(String.valueOf(row.getHatchFrame()));
        return String.join(",", fields);
    }

    private static List<String> positionCells(Position position) {
        List<String> fields = new ArrayList<>();
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getX()));
        fields.add(String.valueOf(position == null ? NOT_EVALUATED : position.getY()));
        return fields;
    }
}
