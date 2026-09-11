package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LearningManagerTest {

    private static final String MAP_NAME = "(4)Polypoid_1.65.scx";
    private static final String OPPONENT_NAME = "TestBot";
    private static final int GAMES_PER_OPENER = 5;

    @Test
    void earlyRushAloneDoesNotForceOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;EarlyRush", "Overpool", MAP_NAME);

        assertNotEquals("Overpool", selected);
        assertEquals("4Pool", selected);
    }

    @Test
    void earlyRushStillRespectsBackToBack4PoolExclusion() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool", "12Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;EarlyRush", "4Pool", MAP_NAME);

        assertNotEquals("4Pool", selected);
        assertEquals("12Pool", selected);
    }

    @Test
    void cannonRushForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "CannonRush", "4Pool", MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void cannonRushAlongsideEarlyRushStillForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "2Gate;CannonRush;EarlyRush", "Overpool", MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void scvRushForcesOverpool() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Terran);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Terran, "4Pool");

        String selected = LearningManager.selectOpenerName(null, factory, opponentRecord,
                "SCVRush", "4Pool", MAP_NAME);

        assertEquals("Overpool", selected);
    }

    @Test
    void openerOverrideTakesPrecedenceOverRushTrigger() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("12Hatch", factory, opponentRecord,
                "CannonRush", "Overpool", MAP_NAME);

        assertEquals("12Hatch", selected);
    }

    @Test
    void openerOverrideTakesPrecedenceOverUcbSelection() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("12Pool", factory, opponentRecord,
                "2Gate;EarlyRush", "Overpool", MAP_NAME);

        assertEquals("12Pool", selected);
    }

    @Test
    void unresolvableOpenerOverrideFallsThroughToRushTrigger() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        OpponentRecord opponentRecord = opponentRecordFavouring(factory, Race.Protoss, "4Pool");

        String selected = LearningManager.selectOpenerName("NotAnOpener", factory, opponentRecord,
                "CannonRush", "Overpool", MAP_NAME);

        assertEquals("Overpool", selected);
    }

    /**
     * 12Pool is benched and stands as the UCB winner. Overpool is the promoted incumbent, losing
     * below the probe gate, and 12Hatch is benched but dormant, so the forced re-probe must still
     * run against the incumbent and select 12Hatch.
     */
    @Test
    void benchedUcbWinnerStillReachesForcedReprobe() {
        OpponentRecord opponentRecord = emptyOpponentRecord();
        appendGames(opponentRecord, "12Hatch", false, LearningManager.PROBE_TRIAL_GAMES);
        appendGames(opponentRecord, "Overpool", true, LearningManager.PROBE_PROMOTION_WINS);
        appendGames(opponentRecord, "Overpool", false, 10);
        appendGames(opponentRecord, "12Pool", false, LearningManager.PROBE_TRIAL_GAMES);
        appendGames(opponentRecord, "Overpool", false, LearningManager.PROBE_EXPOSURE_WINDOW_GAMES);
        List<String> playableOpeners = Arrays.asList("12Hatch", "12Pool", "Overpool");

        assertTrue(LearningManager.isBenched("12Pool", opponentRecord));
        assertTrue(LearningManager.isBenched("12Hatch", opponentRecord));
        assertFalse(LearningManager.isBenched("Overpool", opponentRecord));
        assertEquals("12Hatch", LearningManager.applyDormantReprobePolicy("12Pool", playableOpeners,
                opponentRecord, MAP_NAME));
    }

    /**
     * Every opener is benched: Overpool holds the slot losing, 12Pool has one win in its failed
     * trial, and 12Hatch is dormant. 12Pool is the UCB winner and the failing trials fill the
     * exposure window, yet the forced re-probe must still fire and select the dormant 12Hatch.
     */
    @Test
    void everyArmBenchedStillForcesDormantReprobe() {
        OpponentRecord opponentRecord = emptyOpponentRecord();
        appendGames(opponentRecord, "12Hatch", false, LearningManager.PROBE_TRIAL_GAMES);
        appendGames(opponentRecord, "12Pool", false, 3);
        appendGames(opponentRecord, "Overpool", false, 20);
        appendGames(opponentRecord, "12Pool", true, 1);
        appendGames(opponentRecord, "12Pool", false, 3);
        appendGames(opponentRecord, "Overpool", false, 4);
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        List<String> playableOpeners = Arrays.asList("12Hatch", "12Pool", "Overpool");

        for (String opener : playableOpeners) {
            assertTrue(LearningManager.isBenched(opener, opponentRecord), opener + " must be benched");
        }
        assertEquals("12Pool", WeightedUCBCalculator.findBestStrategy(playableOpeners, MAP_NAME,
                opponentRecord.getMapSpecificOpenerRecord(), opponentRecord.getOpenerRecord(),
                opponentRecord.totalGames(), opponentRecord.getGameTimestamps()));
        assertEquals("12Hatch", LearningManager.selectOpenerName(null, factory, opponentRecord,
                "", "Overpool", MAP_NAME));
    }

    /**
     * Overpool's Protoss transitions: 3HatchMuta has been played to a 40% win rate, while
     * SpeedlingAllIn and 3HatchHydra have never been played. Both untried candidates take their
     * first exposure before 3HatchMuta is chosen again.
     */
    @Test
    void anUntriedBuildOrderCandidateIsChosenBeforeATriedOne() {
        OpponentRecord opponentRecord = emptyOpponentRecord();
        Map<String, Record> buildOrders = opponentRecord.getBuildOrderRecord();
        appendGames(opponentRecord, buildOrders, "3HatchMuta", false, 3);
        appendGames(opponentRecord, buildOrders, "3HatchMuta", true, 2);
        appendGames(opponentRecord, buildOrders, "SpeedlingAllIn", false, 0);
        appendGames(opponentRecord, buildOrders, "3HatchHydra", false, 0);
        List<String> candidates = Arrays.asList("3HatchHydra", "3HatchMuta", "SpeedlingAllIn");

        String first = LearningManager.selectBuildOrderName(candidates, opponentRecord, MAP_NAME);
        appendGames(opponentRecord, buildOrders, first, false, 1);
        String second = LearningManager.selectBuildOrderName(candidates, opponentRecord, MAP_NAME);
        appendGames(opponentRecord, buildOrders, second, false, 1);

        assertNotEquals("3HatchMuta", first);
        assertNotEquals("3HatchMuta", second);
        assertNotEquals(first, second);
        assertEquals("3HatchMuta", LearningManager.selectBuildOrderName(candidates, opponentRecord, MAP_NAME));
    }

    private static OpponentRecord emptyOpponentRecord() {
        return OpponentRecord.builder()
                .name(OPPONENT_NAME)
                .race(Race.Zerg.toString())
                .wins(0)
                .losses(0)
                .openerRecord(new HashMap<>())
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
    }

    private static void appendGames(OpponentRecord opponentRecord, String opener, boolean won, int games) {
        appendGames(opponentRecord, opponentRecord.getOpenerRecord(), opener, won, games);
    }

    private static void appendGames(OpponentRecord opponentRecord, Map<String, Record> records, String strategy,
                                    boolean won, int games) {
        Record record = records
                .computeIfAbsent(strategy, name -> Record.builder().opener(name).wins(0).losses(0).build());
        for (int i = 0; i < games; i++) {
            long timestamp = opponentRecord.getGameTimestamps().size() + 1;
            if (won) {
                record.setWins(record.getWins() + 1);
                record.addWinTimestamp(timestamp);
                opponentRecord.setWins(opponentRecord.getWins() + 1);
            } else {
                record.setLosses(record.getLosses() + 1);
                record.addLossTimestamp(timestamp);
                opponentRecord.setLosses(opponentRecord.getLosses() + 1);
            }
            opponentRecord.getGameTimestamps().add(timestamp);
        }
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
