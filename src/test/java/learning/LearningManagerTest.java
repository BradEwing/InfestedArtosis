package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class LearningManagerTest {

    private static final String MAP_NAME = "(4)Polypoid_1.65.scx";
    private static final String OPPONENT_NAME = "TestBot";
    private static final int GAMES_PER_OPENER = 5;

    @Test
    void earlyRushAloneDoesNotForceOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;EarlyRush", "Overpool", OPPONENT_NAME, MAP_NAME);

        assertNotEquals("Overpool", selected);
        assertEquals("4Pool", selected);
    }

    @Test
    void earlyRushStillRespectsBackToBack4PoolExclusion() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool", "12Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;EarlyRush", "4Pool", OPPONENT_NAME, MAP_NAME);

        assertNotEquals("4Pool", selected);
        assertEquals("12Pool", selected);
    }

    @Test
    void cannonRushForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "CannonRush", "4Pool", OPPONENT_NAME, MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void cannonRushAlongsideEarlyRushStillForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;CannonRush;EarlyRush", "Overpool", OPPONENT_NAME, MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void scvRushForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Terran);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Terran, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "SCVRush", "4Pool", OPPONENT_NAME, MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void openerOverrideTakesPrecedenceOverRushTrigger() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("12Hatch", factory, opponentRecord,
                "CannonRush", "Overpool", OPPONENT_NAME, MAP_NAME);

        assertEquals("12Hatch", selected);
    }

    @Test
    void openerOverrideTakesPrecedenceOverUcbSelection() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("12Pool", factory, opponentRecord,
                "2Gate;EarlyRush", "Overpool", OPPONENT_NAME, MAP_NAME);

        assertEquals("12Pool", selected);
    }

    @Test
    void unresolvableOpenerOverrideFallsThroughToRushTrigger() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("NotAnOpener", factory, opponentRecord,
                "CannonRush", "Overpool", OPPONENT_NAME, MAP_NAME);

        assertEquals("Overpool", selected);
    }

    private static OpponentRecord opponentRecordFavouring(BuildOrderFactory factory, Race opponentRace, String favouredOpener) {
        return opponentRecordFavouring(factory, opponentRace, favouredOpener, null);
    }

    /**
     * Builds a fully determined opener history: every opener is given the same number of timestamped games, so the
     * D-UCB exploration term is identical across arms and the sample mean alone decides the winner. The favoured
     * opener wins every game, the runner-up wins all but one, and every other opener loses every game.
     */
    private static OpponentRecord opponentRecordFavouring(BuildOrderFactory factory, Race opponentRace, String favouredOpener,
            String runnerUpOpener) {
        Map<String, Record> openerRecords = new HashMap<>();
        long timestamp = 1000L;
        int wins = 0;
        int losses = 0;

        for (String openerName : factory.getOpenerNames()) {
            Record record = Record.builder()
                    .opener(openerName)
                    .wins(0)
                    .losses(0)
                    .build();

            int winsForOpener = 0;
            if (openerName.equals(favouredOpener)) {
                winsForOpener = GAMES_PER_OPENER;
            } else if (openerName.equals(runnerUpOpener)) {
                winsForOpener = GAMES_PER_OPENER - 1;
            }

            for (int i = 0; i < GAMES_PER_OPENER; i++) {
                if (i < winsForOpener) {
                    record.setWins(record.getWins() + 1);
                    record.addWinTimestamp(timestamp);
                    wins++;
                } else {
                    record.setLosses(record.getLosses() + 1);
                    record.addLossTimestamp(timestamp);
                    losses++;
                }
                timestamp++;
            }

            openerRecords.put(openerName, record);
        }

        return OpponentRecord.builder()
                .name(OPPONENT_NAME)
                .race(opponentRace.toString())
                .wins(wins)
                .losses(losses)
                .openerRecord(openerRecords)
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
    }
}
