package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BunkerLoggerTest {

    private static final BunkerAdvanceEvent.Bunker BUNKER = new BunkerAdvanceEvent.Bunker(7, 1000, 1000, 350);

    @AfterEach
    void clearSink() {
        BunkerTelemetry.clear();
    }

    private static int columnIndex(String column) {
        String[] columns = BunkerLogger.HEADER.split(",", -1);
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] fields(String row) {
        String[] fields = row.split(",", -1);
        assertEquals(BunkerLogger.COLUMNS.length, fields.length);
        return fields;
    }

    private static BunkerAdvanceEvent advance(int frame, String squad, BunkerAdvanceReason reason) {
        return new BunkerAdvanceEvent(frame, new BunkerAdvanceEvent.Squad(squad, 40, 30, 640, 720), reason, 150, 100,
                1.69, BunkerAdvanceEntry.ADVANCE, BUNKER);
    }

    @Test
    void aHeldAdvanceRowCarriesThePricedStrengthTheRatioAndTheBunker() {
        String[] fields = fields(BunkerLogger.advanceRow("game-1", advance(7000, "squad-a",
                BunkerAdvanceReason.HELD_LOSS)));

        assertEquals("game-1", fields[columnIndex("game_id")]);
        assertEquals("7000", fields[columnIndex("frame")]);
        assertEquals("BUNKER_ADVANCE", fields[columnIndex("row_type")]);
        assertEquals("HELD", fields[columnIndex("event")]);
        assertEquals("HELD_LOSS", fields[columnIndex("reason")]);
        assertEquals("150.0000", fields[columnIndex("own_strength")]);
        assertEquals("100.0000", fields[columnIndex("bunker_price")]);
        assertEquals("1.5000", fields[columnIndex("ratio")]);
        assertEquals("1.6900", fields[columnIndex("release_ratio")]);
        assertEquals("30", fields[columnIndex("ling_count")]);
        assertEquals("7", fields[columnIndex("bunker_id")]);
        assertEquals("1000", fields[columnIndex("bunker_x")]);
        assertEquals("350", fields[columnIndex("bunker_hp")]);
    }

    @Test
    void anAllowedAdvanceWithNoLossOnRecordLeavesThePriceColumnsBlank() {
        BunkerAdvanceEvent event = new BunkerAdvanceEvent(7000, new BunkerAdvanceEvent.Squad("squad-a", 40, 30, 640, 720),
                BunkerAdvanceReason.NO_LOSS, 150, 0, 0, BunkerAdvanceEntry.FIGHT_LOCK, BunkerAdvanceEvent.Bunker.NONE);

        String[] fields = fields(BunkerLogger.advanceRow("game-1", event));

        assertEquals("ALLOWED", fields[columnIndex("event")]);
        assertEquals("", fields[columnIndex("bunker_price")]);
        assertEquals("", fields[columnIndex("ratio")]);
        assertEquals("", fields[columnIndex("bunker_id")]);
        assertEquals("", fields[columnIndex("bunker_hp")]);
    }

    @Test
    void anEngagementRowCarriesTheLossesTheBunkerHitPointsAndWhetherItBroke() {
        BunkerEngagements engagements = new BunkerEngagements();
        engagements.onSample(100, java.util.Collections.singletonList(
                new BunkerEngagements.BunkerSample(7, new Position(1000, 1000), 350)),
                java.util.Collections.nCopies(BunkerEngagements.MIN_UNITS,
                        new BunkerEngagements.OurUnit(new Position(900, 1000))));
        engagements.onOurDeath(new Position(900, 1000), true);
        engagements.onEnemyDeath(new Position(1000, 1000));
        BunkerEngagements.Closed closed = engagements.onSample(200, java.util.Collections.emptyList(),
                java.util.Collections.emptyList()).get(0);

        String[] fields = fields(BunkerLogger.engagementRow("game-1", closed));

        assertEquals("BUNKER_ENGAGEMENT", fields[columnIndex("row_type")]);
        assertEquals("1", fields[columnIndex("engagement_id")]);
        assertEquals("100", fields[columnIndex("start_frame")]);
        assertEquals("200", fields[columnIndex("end_frame")]);
        assertEquals("1", fields[columnIndex("our_lost")]);
        assertEquals("1", fields[columnIndex("lings_lost")]);
        assertEquals("1", fields[columnIndex("enemy_lost")]);
        assertEquals("350", fields[columnIndex("hp_start")]);
        assertEquals("0", fields[columnIndex("hp_end")]);
        assertEquals("1", fields[columnIndex("broken")]);
    }

    @Test
    void aHoldRowNamesTheChange() {
        String[] fields = fields(BunkerLogger.holdRow("game-1", 4000, "HOLD_END", "BROKEN"));

        assertEquals("BUNKER_HOLD", fields[columnIndex("row_type")]);
        assertEquals("HOLD_END", fields[columnIndex("event")]);
        assertEquals("BROKEN", fields[columnIndex("reason")]);
    }

    @Test
    void anEconRowCarriesTheStanceAndTheDronesPlannedAndMade() {
        String[] fields = fields(BunkerLogger.econRow("game-1",
                new BunkerStanceEvent(9000, "STANCE_END", "ATTACKING", 2, 20, 18, 4, 3)));

        assertEquals("BUNKER_ECON", fields[columnIndex("row_type")]);
        assertEquals("STANCE_END", fields[columnIndex("event")]);
        assertEquals("ATTACKING", fields[columnIndex("reason")]);
        assertEquals("2", fields[columnIndex("stance_id")]);
        assertEquals("20", fields[columnIndex("drones")]);
        assertEquals("18", fields[columnIndex("workers")]);
        assertEquals("4", fields[columnIndex("extra_planned")]);
        assertEquals("3", fields[columnIndex("extra_made")]);
    }

    @Test
    void aSquadsIdenticalDecisionIsWrittenOnceUntilItChangesOrTheRepeatIntervalPasses(@TempDir Path directory)
            throws IOException {
        Path file = directory.resolve("telemetry_bunker.csv");
        TelemetryWriter writer = new TelemetryWriter(file, BunkerLogger.HEADER);
        BunkerLogger logger = new BunkerLogger(null, null, "game-1", writer);

        logger.onAdvance(advance(1000, "squad-a", BunkerAdvanceReason.HELD_LOSS));
        logger.onAdvance(advance(1100, "squad-a", BunkerAdvanceReason.HELD_LOSS));
        logger.onAdvance(advance(1200, "squad-b", BunkerAdvanceReason.HELD_LOSS));
        logger.onAdvance(advance(1300, "squad-a", BunkerAdvanceReason.RELEASED_STRENGTH));
        logger.onAdvance(advance(1300 + BunkerLogger.ADVANCE_REPEAT_FRAMES, "squad-a",
                BunkerAdvanceReason.RELEASED_STRENGTH));
        writer.flush();

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(BunkerLogger.HEADER, lines.get(0));
        assertEquals(5, lines.size());
    }

    @Test
    void anAdvanceRowCarriesTheSquadSizeCentreAndTheBranchThatReadIt() {
        String[] fields = fields(BunkerLogger.advanceRow("game-1", advance(7000, "squad-a",
                BunkerAdvanceReason.HELD_LOSS)));

        assertEquals("40", fields[columnIndex("squad_size")]);
        assertEquals("640", fields[columnIndex("squad_x")]);
        assertEquals("720", fields[columnIndex("squad_y")]);
        assertEquals("ADVANCE", fields[columnIndex("entry")]);
    }

    @Test
    void aLossRowCarriesTheSquadTheUnitsLostAndThePrice() {
        String[] fields = fields(BunkerLogger.lossRow("game-1",
                new BunkerLossEvent(7000, "squad-a", 18, 5, 120, BUNKER)));

        assertEquals("LOSS_RECORDED", fields[columnIndex("row_type")]);
        assertEquals("squad-a", fields[columnIndex("squad_id")]);
        assertEquals("18", fields[columnIndex("squad_size")]);
        assertEquals("5", fields[columnIndex("units_lost")]);
        assertEquals("120.0000", fields[columnIndex("bunker_price")]);
        assertEquals("7", fields[columnIndex("bunker_id")]);
    }

    @Test
    void anAttackRowIsGatedWhenTheGateReadTheSquadAndUnreadOtherwise() {
        String[] gated = fields(BunkerLogger.attackRow("game-1",
                new BunkerAttackEvent(7000, "squad-a", 18, 640, 720, BunkerAdvanceEntry.FIGHT_LOCK, BUNKER)));
        String[] unread = fields(BunkerLogger.attackRow("game-1",
                new BunkerAttackEvent(7000, "squad-b", 18, 640, 720, null, BUNKER)));

        assertEquals("BUNKER_ATTACK", gated[columnIndex("row_type")]);
        assertEquals("GATED", gated[columnIndex("event")]);
        assertEquals("FIGHT_LOCK", gated[columnIndex("entry")]);
        assertEquals("UNREAD", unread[columnIndex("event")]);
        assertEquals("", unread[columnIndex("entry")]);
    }
}
