package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RacePriorTest {

    private static final String PRIOR_CSV = String.join("\n",
            "race,kind,arm,pseudo_wins,pseudo_games",
            "Terran,opener,9Hatch,1.6,3",
            "Terran,opener,12Pool,0,3",
            "Terran,opener,4Pool,0,5",
            "Terran,build,3HatchLurker,2,3",
            "Terran,build,SpeedlingT,1,3",
            "Unknown,opener,9PoolSpeed,3,3",
            "Unknown,opener,12Pool,0,3",
            "Unknown,build,SpeedlingR,2,3");

    private static RacePrior parse(String csv) throws IOException {
        return RacePrior.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static OpponentRecord freshRecord(List<String> openers, List<String> builds) {
        OpponentRecord record = OpponentRecord.builder()
                .name("Opponent")
                .race("Terran")
                .openerRecord(new HashMap<>())
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
        for (String opener : openers) {
            record.getOpenerRecord().put(opener, Record.builder().opener(opener).build());
        }
        for (String build : builds) {
            record.getBuildOrderRecord().put(build, Record.builder().opener(build).build());
        }
        return record;
    }

    private static OpponentRecord terranRecord() {
        return freshRecord(Arrays.asList("9Hatch", "12Pool", "4Pool", "Overpool"),
                Arrays.asList("3HatchLurker", "2HatchMuta"));
    }

    private static RacePrior.Report seed(OpponentRecord record, String race) throws IOException {
        return parse(PRIOR_CSV).seedIfNew(true, 0, record, new LearningRecordAccumulator("Opponent", Race.Terran),
                race);
    }

    @Test
    void emptyHistorySeedsTheRacesDiscountedCounts() throws IOException {
        OpponentRecord record = terranRecord();

        RacePrior.Report report = seed(record, "Terran");

        Record nineHatch = record.getOpenerRecord().get("9Hatch");
        assertTrue(report.applied());
        assertEquals(3, report.openers());
        assertEquals(1, report.builds());
        assertEquals(0, nineHatch.games());
        assertEquals(1.6, nineHatch.getPriorWins(), 1e-9);
        assertEquals(3.0, nineHatch.discountedGames(record.getGameTimestamps()), 1e-9);
        assertEquals(1.6 / 3.0, nineHatch.discountedMean(record.getGameTimestamps()), 1e-9);
        assertEquals(5.0, record.getOpenerRecord().get("4Pool").discountedGames(record.getGameTimestamps()), 1e-9);
        assertEquals(0.0, record.getOpenerRecord().get("4Pool").discountedMean(record.getGameTimestamps()), 1e-9);
        assertEquals(2.0 / 3.0, record.getBuildOrderRecord().get("3HatchLurker")
                .discountedMean(record.getGameTimestamps()), 1e-9);
    }

    @Test
    void seededEvidenceAgesAtGammaPerRealGameWhenRebuiltFromHistory() throws IOException {
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator("Opponent", Race.Terran);
        OpponentRecord record = accumulator.reconstruct(new LearningHistory(Arrays.asList(
                realGame(1_000L, "12Pool", true), realGame(2_000L, "12Pool", false), realGame(3_000L, "12Pool", true))));
        record.getOpenerRecord().put("9Hatch", Record.builder().opener("9Hatch").build());

        RacePrior.Report report = parse(PRIOR_CSV).seedIfNew(true, 3, record, accumulator, "Terran");

        Record nineHatch = record.getOpenerRecord().get("9Hatch");
        assertTrue(report.applied());
        assertEquals(3.0 * Math.pow(UCBSelectionPolicy.GAMMA, 3), nineHatch.discountedGames(record.getGameTimestamps()), 1e-9);
        assertEquals(1.6 / 3.0, nineHatch.discountedMean(record.getGameTimestamps()), 1e-9);
        assertEquals(3, record.totalGames());
        assertEquals(3, record.getGameTimestamps().size());
    }

    private static GameRecord realGame(long timestamp, String opener, boolean won) {
        return GameRecord.builder().timestamp(timestamp).mapName("MapA").opener(opener).buildOrder(opener)
                .isWinner(won).build();
    }

    @Test
    void seededGamesNeverTouchTheOpponentTotalsClockMapsOrPairs() throws IOException {
        OpponentRecord record = terranRecord();

        seed(record, "Terran");

        assertEquals(0, record.totalGames());
        assertEquals(0, record.getWins());
        assertEquals(0, record.getLosses());
        assertTrue(record.getGameTimestamps().isEmpty());
        assertTrue(record.getMapSpecificOpenerRecord().isEmpty());
        assertTrue(record.getMapSpecificBuildOrderRecord().isEmpty());
        assertTrue(record.getOpenerBuildPairs().isEmpty());
        assertEquals(1, record.selectionGames());
    }

    @Test
    void seededGamesAreNotSelectionsForTheTrialLog() throws IOException {
        OpponentRecord record = terranRecord();
        seed(record, "Terran");

        OpenerSelectionLog log = OpenerSelectionLog.from(record.getOpenerRecord().get("9Hatch"),
                record.getGameTimestamps(), LearningManager.PROBE_DORMANT_GAMES);

        assertEquals(0, log.trialCount());
        assertEquals(OpenerSelectionLog.NEVER_SELECTED, log.gamesSinceLastSelection());
        assertFalse(LearningManager.isBenched("12Pool", record));
    }

    @Test
    void nonEmptyHistoryIgnoresThePrior() throws IOException {
        OpponentRecord record = terranRecord();

        RacePrior.Report report = parse(PRIOR_CSV).seedIfNew(true, RacePrior.HISTORY_HORIZON_GAMES, record,
                new LearningRecordAccumulator("Opponent", Race.Terran), "Terran");

        assertFalse(report.applied());
        assertEquals(0, record.getOpenerRecord().get("9Hatch").games());
        assertEquals(0, record.selectionGames());
    }

    @Test
    void switchOffSeedsNothing() throws IOException {
        OpponentRecord record = terranRecord();

        RacePrior.Report report = parse(PRIOR_CSV).seedIfNew(false, 0, record,
                new LearningRecordAccumulator("Opponent", Race.Terran), "Terran");

        assertFalse(report.applied());
        assertEquals("applied=n;race=Terran;openers=0;builds=0", report.label());
        assertEquals(0, record.selectionGames());
        assertEquals(0, record.getOpenerRecord().get("9Hatch").games());
    }

    @Test
    void unknownRaceUsesTheUnknownRows() throws IOException {
        OpponentRecord record = freshRecord(Arrays.asList("9PoolSpeed", "12Pool", "9Hatch"),
                Arrays.asList("SpeedlingR"));

        RacePrior.Report report = seed(record, RacePrior.raceKey(Race.Random));

        assertEquals("Unknown", RacePrior.raceKey(Race.Random));
        assertEquals("Unknown", RacePrior.raceKey(Race.Unknown));
        assertEquals("Terran", RacePrior.raceKey(Race.Terran));
        assertEquals(2, report.openers());
        assertEquals(3.0, record.getOpenerRecord().get("9PoolSpeed").getPriorWins(), 1e-9);
        assertEquals(0, record.getOpenerRecord().get("9Hatch").games());
        assertEquals(2.0, record.getBuildOrderRecord().get("SpeedlingR").getPriorWins(), 1e-9);
    }

    @Test
    void armsThatAreNotLegalCandidatesAreIgnoredAndAbsentArmsStayUntried() throws IOException {
        OpponentRecord record = terranRecord();

        RacePrior.Report report = seed(record, "Terran");

        assertFalse(record.getBuildOrderRecord().containsKey("SpeedlingT"));
        assertEquals(1, report.builds());
        assertEquals(0, record.getOpenerRecord().get("Overpool").games());
        assertEquals(0, record.getBuildOrderRecord().get("2HatchMuta").games());
    }

    @Test
    void seededRecordSkipsTheUntriedSweep() throws IOException {
        OpponentRecord record = terranRecord();
        record.getOpenerRecord().remove("Overpool");
        seed(record, "Terran");
        List<String> playable = Arrays.asList("9Hatch", "12Pool", "4Pool");

        String picked = WeightedUCBCalculator.findBestStrategy(playable, "MapA", record.getMapSpecificOpenerRecord(),
                record.getOpenerRecord(), record.selectionGames(), record.getGameTimestamps());

        assertEquals("9Hatch", picked);
    }

    @Test
    void seededRecordIsMarkedPriorOnlyUntilPlayed() throws IOException {
        OpponentRecord record = terranRecord();
        seed(record, "Terran");
        Record nineHatch = record.getOpenerRecord().get("9Hatch");

        assertTrue(nineHatch.isPriorOnly());
        assertFalse(record.getOpenerRecord().get("Overpool").isPriorOnly());

        new LearningRecordAccumulator("Opponent", Race.Terran).apply(record, GameRecord.builder()
                .timestamp(5_000L).mapName("MapA").opener("9Hatch").buildOrder("9Hatch").isWinner(false).build());

        assertFalse(nineHatch.isPriorOnly());
        assertEquals(1, nineHatch.games());
    }

    @Test
    void safetyNetPlaysAPriorOnlyArmWhenTheLeaderDropsBelowTheGate() throws IOException {
        OpponentRecord record = terranRecord();
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator("Opponent", Race.Terran);
        for (int i = 0; i < 8; i++) {
            accumulator.apply(record, GameRecord.builder().timestamp(1_000L + i).mapName("MapA").opener("9Hatch")
                    .buildOrder("9Hatch").isWinner(false).build());
        }
        parse(PRIOR_CSV).seedIfNew(true, 8, record, accumulator, "Terran");
        List<String> playable = Arrays.asList("9Hatch", "12Pool", "4Pool", "Overpool");

        String picked = PriorSafetyNet.apply("9Hatch", playable, record.getOpenerRecord(),
                record.getGameTimestamps());

        assertTrue(record.getOpenerRecord().get("9Hatch").discountedMean(record.getGameTimestamps())
                < LearningManager.PROBE_GATE_WIN_RATE);
        assertNotEquals("9Hatch", picked);
        assertTrue(record.getOpenerRecord().get(picked).isPriorOnly());
    }

    @Test
    void safetyNetPicksTheHighestPriorMeanAmongPriorOnlyArms() throws IOException {
        OpponentRecord record = freshRecord(Arrays.asList("9Hatch", "12Pool", "4Pool"), Arrays.asList());
        LearningRecordAccumulator accumulator = new LearningRecordAccumulator("Opponent", Race.Terran);
        accumulator.apply(record, GameRecord.builder().timestamp(9_000L).mapName("MapA").opener("9Hatch")
                .buildOrder("9Hatch").isWinner(false).build());
        accumulator.applyPrior(record, true, "12Pool", 1.0, 3.0);
        accumulator.applyPrior(record, true, "4Pool", 2.0, 3.0);

        String picked = PriorSafetyNet.apply("9Hatch", Arrays.asList("9Hatch", "12Pool", "4Pool"),
                record.getOpenerRecord(), record.getGameTimestamps());

        assertEquals("4Pool", picked);
    }

    @Test
    void safetyNetKeepsALeaderAboveTheGate() throws IOException {
        OpponentRecord record = terranRecord();
        seed(record, "Terran");
        List<String> playable = Arrays.asList("9Hatch", "12Pool", "4Pool", "Overpool");

        assertEquals("9Hatch", PriorSafetyNet.apply("9Hatch", playable, record.getOpenerRecord(),
                record.getGameTimestamps()));
    }

    @Test
    void safetyNetIsInertWithoutAPrior() {
        OpponentRecord record = terranRecord();
        record.getOpenerRecord().get("9Hatch").setLosses(5);
        record.getOpenerRecord().get("9Hatch").getLossTimestamps().addAll(Arrays.asList(1L, 2L, 3L, 4L, 5L));
        record.getGameTimestamps().addAll(Arrays.asList(1L, 2L, 3L, 4L, 5L));

        assertEquals("9Hatch", PriorSafetyNet.apply("9Hatch", Arrays.asList("9Hatch", "12Pool"),
                record.getOpenerRecord(), record.getGameTimestamps()));
    }

    @Test
    void forcedReprobeTreatsAPriorOnlyLeaderLikeAnUntriedOne() throws IOException {
        OpponentRecord record = terranRecord();
        seed(record, "Terran");

        assertNull(LearningManager.selectForcedReprobe("12Pool",
                Arrays.asList("9Hatch", "12Pool", "4Pool", "Overpool"), record, "MapA"));
    }

    @Test
    void malformedRowsAndMissingResourceYieldNoPrior() throws IOException {
        RacePrior prior = parse("garbage\n,,,,\nTerran,opener,X,notanumber,3\nTerran,other,X,1,3\n"
                + "Terran,opener,Y,1,0\n# comment\nTerran,opener,Z,1,2");

        assertEquals(1, prior.arms("Terran", RacePrior.KIND_OPENER).size());
        assertEquals("Z", prior.arms("Terran", RacePrior.KIND_OPENER).get(0).name());
        assertTrue(RacePrior.empty().arms("Terran", RacePrior.KIND_OPENER).isEmpty());
    }

    @Test
    void pseudoWinsAreClampedToPseudoGames() throws IOException {
        RacePrior prior = parse("Terran,opener,Z,9,2");

        assertEquals(2, prior.arms("Terran", RacePrior.KIND_OPENER).get(0).wins());
    }

    @Test
    void bundledResourceIsOnTheClasspath() {
        RacePrior prior = RacePrior.load();

        for (String race : new String[] {"Terran", "Protoss", "Zerg", "Unknown"}) {
            assertFalse(prior.arms(race, RacePrior.KIND_OPENER).isEmpty(), race);
        }
        Map<String, Integer> seen = new HashMap<>();
        for (RacePrior.Arm arm : prior.arms("Terran", RacePrior.KIND_OPENER)) {
            seen.merge(arm.name(), 1, Integer::sum);
            assertTrue(arm.wins() <= arm.games());
        }
        assertTrue(seen.values().stream().allMatch(count -> count == 1));
    }
}
