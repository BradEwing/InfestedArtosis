package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.AirSquad;
import unit.squad.Squad;
import unit.squad.SquadStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirReinforcementLoggerTest {

    @AfterEach
    void clearSink() {
        AirReinforcementTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = AirReinforcementLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private static AirReinforcementRow squadRow(int frame, AirReinforcementRow.Event event) {
        return AirReinforcementRow.builder().frame(frame).event(event).squadId("squad-1").build();
    }

    @Test
    void aRouteRowCarriesTheHeaderColumnCountAndItsPath() {
        AirReinforcementRow route = AirReinforcementRow.builder()
                .frame(12290)
                .event(AirReinforcementRow.Event.ROUTE)
                .squadId("squad-1")
                .squadStatus(SquadStatus.RALLY)
                .targetSquadId("squad-2")
                .targetStatus(SquadStatus.HARASS)
                .center(new Position(1000, 2000))
                .targetCenter(new Position(3000, 2000))
                .mutas(4)
                .waypoints(3)
                .pathLength(2350.5)
                .build();

        String[] fields = AirReinforcementLogger.row("game-1", route).split(",", -1);

        assertEquals(AirReinforcementLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("ROUTE", fields[columnIndex("event")]);
        assertEquals("RALLY", fields[columnIndex("squad_status")]);
        assertEquals("HARASS", fields[columnIndex("target_status")]);
        assertEquals("3000", fields[columnIndex("target_x")]);
        assertEquals("4", fields[columnIndex("mutas")]);
        assertEquals("3", fields[columnIndex("waypoints")]);
        assertEquals("-1", fields[columnIndex("unit_id")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_mate_px")]);
        assertEquals("-1", fields[columnIndex("hatch_frame")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_squad_mate_px")]);
        assertEquals("-1", fields[columnIndex("zone_threats")]);
        assertEquals("-1.0000", fields[columnIndex("detour_px")]);
        assertEquals("-1", fields[columnIndex("in_flight")]);
        assertEquals("-1", fields[columnIndex("link_frames")]);
    }

    @Test
    void linkUpRowsCarryTheZonesTheDetourAndTheFlightCounts() {
        AirReinforcementRow route = AirReinforcementRow.builder().frame(12290).event(AirReinforcementRow.Event.ROUTE)
                .squadId("squad-1").zoneThreats(3).detour(410.5).inFlight(2).build();
        AirReinforcementRow join = AirReinforcementRow.builder().frame(12600).event(AirReinforcementRow.Event.JOIN)
                .squadId("squad-1").zoneThreats(3).inFlight(1).linkFrames(310).build();
        AirReinforcementRow hold = AirReinforcementRow.builder().frame(12290).event(AirReinforcementRow.Event.HOLD)
                .squadId("squad-2").inFlight(2).build();

        String[] routeFields = AirReinforcementLogger.row("game-1", route).split(",", -1);
        String[] joinFields = AirReinforcementLogger.row("game-1", join).split(",", -1);
        String[] holdFields = AirReinforcementLogger.row("game-1", hold).split(",", -1);

        assertEquals("3", routeFields[columnIndex("zone_threats")]);
        assertEquals(Csv.format(410.5), routeFields[columnIndex("detour_px")]);
        assertEquals("2", routeFields[columnIndex("in_flight")]);
        assertEquals("310", joinFields[columnIndex("link_frames")]);
        assertEquals("HOLD", holdFields[columnIndex("event")]);
        assertEquals("2", holdFields[columnIndex("in_flight")]);
    }

    @Test
    void aRefusalIsWrittenOncePerEpisode(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(AirReinforcementLogger.FILE);
        AirReinforcementLogger logger = new AirReinforcementLogger(null, null, "game-1",
                new TelemetryWriter(file, AirReinforcementLogger.HEADER));
        AirReinforcementTelemetry.register(logger);

        AirReinforcementTelemetry.row(squadRow(12000, AirReinforcementRow.Event.REFUSED));
        AirReinforcementTelemetry.row(squadRow(12001, AirReinforcementRow.Event.REFUSED));
        AirReinforcementTelemetry.row(squadRow(12100, AirReinforcementRow.Event.ROUTE));
        AirReinforcementTelemetry.row(squadRow(12200, AirReinforcementRow.Event.REFUSED));
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(AirReinforcementLogger.HEADER, lines.get(0));
        List<String> events = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            events.add(line.split(",", -1)[columnIndex("event")]);
        }
        assertEquals(Arrays.asList("REFUSED", "ROUTE", "REFUSED"), events);
    }

    @Test
    void theNearestMateIsTheClosestOtherMutalisk() {
        Map<Integer, Position> positions = new HashMap<>();
        positions.put(1, new Position(0, 0));
        positions.put(2, new Position(300, 400));
        positions.put(3, new Position(0, 700));

        assertEquals(500.0, AirReinforcementLogger.nearestMateDistance(1, positions, other -> true), 1e-9);
        assertEquals(-1.0, AirReinforcementLogger.nearestMateDistance(9, positions, other -> true), 1e-9);
        assertEquals(-1.0, AirReinforcementLogger.nearestMateDistance(1,
                Collections.singletonMap(1, new Position(0, 0)), other -> true), 1e-9);
    }

    @Test
    void theNearestSquadMateIgnoresMutalisksOfOtherSquads() {
        Map<Integer, Position> positions = new HashMap<>();
        positions.put(1, new Position(0, 0));
        positions.put(2, new Position(100, 0));
        positions.put(3, new Position(0, 700));

        assertEquals(700.0, AirReinforcementLogger.nearestMateDistance(1, positions, other -> other == 3), 1e-9);
        assertEquals(-1.0, AirReinforcementLogger.nearestMateDistance(1, positions, other -> false), 1e-9);
    }

    @Test
    void anEmptySquadIsNotCountedAsActive() {
        Squad harass = new AirSquad();
        harass.setStatus(SquadStatus.HARASS);

        assertEquals(0, AirReinforcementLogger.activeAirSquads(Collections.singletonList(harass)));
    }
}
