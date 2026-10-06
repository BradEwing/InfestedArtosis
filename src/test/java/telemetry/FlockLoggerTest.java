package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import unit.squad.AirFlock;
import unit.squad.SquadStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlockLoggerTest {

    @AfterEach
    void clearSink() {
        FlockTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = FlockLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void aSampleRowCarriesTheSpreadInHeaderOrder() {
        FlockRow sample = FlockRow.builder()
                .frame(11450)
                .squadId("squad-1")
                .event(FlockRow.Event.SAMPLE)
                .status(SquadStatus.FIGHT)
                .mutas(5)
                .centroid(new Position(2687, 3246))
                .medianDistance(215.5)
                .maxDistance(897)
                .regrouping(1)
                .build();

        String[] fields = FlockLogger.row("game-1", sample).split(",", -1);

        assertEquals(FlockLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("SAMPLE", fields[columnIndex("event")]);
        assertEquals("FIGHT", fields[columnIndex("status")]);
        assertEquals("2687", fields[columnIndex("centroid_x")]);
        assertEquals("215.5000", fields[columnIndex("median_distance")]);
        assertEquals("897.0000", fields[columnIndex("max_distance")]);
        assertEquals("1", fields[columnIndex("regrouping")]);
        assertEquals("-1", fields[columnIndex("unit_id")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_mate_distance")]);
        assertEquals("-1", fields[columnIndex("regrouping_ids")]);
        assertEquals("-1", fields[columnIndex("regrouping_armed")]);
        assertEquals("-1", fields[columnIndex("last_muta")]);
    }

    @Test
    void theNewColumnsAreAppendedAfterTheOriginalHeader() {
        assertTrue(FlockLogger.HEADER.startsWith("game_id,frame,squad_id,event,status,mutas,centroid_x,centroid_y,"
                + "median_distance,max_distance,regrouping,unit_id,nearest_mate_distance,"));
        assertTrue(FlockLogger.HEADER.endsWith(",regrouping_ids,regrouping_armed,last_muta,"
                + "retreat_shared,retreat_anchor,retreat_flee,retreat_branch"));
    }

    @Test
    void aRetreatSampleRowCountsTheMembersOfEachBranch() {
        FlockRow sample = FlockRow.builder()
                .frame(14544)
                .squadId("squad-1")
                .event(FlockRow.Event.SAMPLE)
                .status(SquadStatus.RETREAT)
                .mutas(3)
                .retreatShared(1)
                .retreatAnchor(1)
                .retreatFlee(1)
                .build();

        String[] fields = FlockLogger.row("game-1", sample).split(",", -1);

        assertEquals(FlockLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("1", fields[columnIndex("retreat_shared")]);
        assertEquals("1", fields[columnIndex("retreat_anchor")]);
        assertEquals("1", fields[columnIndex("retreat_flee")]);
        assertEquals("-1", fields[columnIndex("retreat_branch")]);
    }

    @Test
    void aLossRowNamesTheBranchOfTheDeadMutaAndIsMinusOneOutsideRetreat() {
        FlockRow retreating = FlockRow.builder()
                .frame(15205)
                .event(FlockRow.Event.MUTA_LOST)
                .unitId(266)
                .retreatBranch(AirFlock.RetreatBranch.FLEE)
                .build();
        FlockRow fighting = FlockRow.builder().frame(15205).event(FlockRow.Event.MUTA_LOST).unitId(266).build();

        String[] lost = FlockLogger.row("game-1", retreating).split(",", -1);
        String[] other = FlockLogger.row("game-1", fighting).split(",", -1);

        assertEquals("FLEE", lost[columnIndex("retreat_branch")]);
        assertEquals("-1", lost[columnIndex("retreat_shared")]);
        assertEquals("-1", other[columnIndex("retreat_branch")]);
    }

    @Test
    void aSampleRowListsTheRegroupingIdsAscendingAndHowManyAreArmed() {
        FlockRow sample = FlockRow.builder()
                .frame(12888)
                .squadId("squad-1")
                .event(FlockRow.Event.SAMPLE)
                .status(SquadStatus.FIGHT)
                .mutas(5)
                .centroid(new Position(2900, 1500))
                .regrouping(2)
                .regroupingIds(new HashSet<>(Arrays.asList(250, 243)))
                .regroupingArmed(1)
                .build();

        String[] fields = FlockLogger.row("game-1", sample).split(",", -1);

        assertEquals(FlockLogger.HEADER.split(",", -1).length, fields.length);
        assertEquals("243;250", fields[columnIndex("regrouping_ids")]);
        assertEquals("1", fields[columnIndex("regrouping_armed")]);
        assertEquals("-1", fields[columnIndex("last_muta")]);
    }

    @Test
    void anEmptyRegroupSetIsWrittenAsAnEmptyField() {
        FlockRow sample = FlockRow.builder()
                .event(FlockRow.Event.SAMPLE)
                .regroupingIds(Collections.emptySet())
                .regroupingArmed(0)
                .build();

        String[] fields = FlockLogger.row("game-1", sample).split(",", -1);

        assertEquals("", fields[columnIndex("regrouping_ids")]);
        assertEquals("0", fields[columnIndex("regrouping_armed")]);
    }

    @Test
    void aRegroupRowCarriesTheMutaAndWhetherItKeptItsTarget() {
        FlockRow regroup = FlockRow.builder()
                .frame(12912)
                .squadId("squad-1")
                .event(FlockRow.Event.REGROUP)
                .status(SquadStatus.FIGHT)
                .mutas(5)
                .centroid(new Position(2836, 1544))
                .unitId(243)
                .nearestMateDistance(210)
                .regroupingArmed(1)
                .build();

        String[] fields = FlockLogger.row("game-1", regroup).split(",", -1);

        assertEquals("REGROUP", fields[columnIndex("event")]);
        assertEquals("243", fields[columnIndex("unit_id")]);
        assertEquals("210.0000", fields[columnIndex("nearest_mate_distance")]);
        assertEquals("1", fields[columnIndex("regrouping_armed")]);
        assertEquals("-1", fields[columnIndex("regrouping_ids")]);
    }

    @Test
    void aLossWithoutASquadIsWrittenWithNoneAndMinusOnes(@TempDir Path directory) throws IOException {
        Path file = directory.resolve(FlockLogger.FILE);
        FlockLogger logger = new FlockLogger(null, "game-1", new TelemetryWriter(file, FlockLogger.HEADER));
        FlockTelemetry.register(logger);

        FlockTelemetry.row(FlockRow.builder()
                .frame(15603)
                .event(FlockRow.Event.MUTA_LOST)
                .unitId(265)
                .centroid(new Position(2486, 4053))
                .build());
        logger.onEnd();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(FlockLogger.HEADER, lines.get(0));
        String[] fields = lines.get(1).split(",", -1);
        assertEquals("NONE", fields[columnIndex("squad_id")]);
        assertEquals("MUTA_LOST", fields[columnIndex("event")]);
        assertEquals("265", fields[columnIndex("unit_id")]);
        assertEquals("-1", fields[columnIndex("mutas")]);
        assertEquals("-1.0000", fields[columnIndex("nearest_mate_distance")]);
        assertEquals("-1", fields[columnIndex("last_muta")]);
    }
}
