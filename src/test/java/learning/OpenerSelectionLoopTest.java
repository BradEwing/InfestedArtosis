package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closed-loop harness that repeatedly drives LearningManager.selectOpenerName against a
 * synthetic opener history, threading lastGameOpener and a map rotation, and appends each
 * result back into the record under deterministic outcome models.
 */
public class OpenerSelectionLoopTest {

    private static final String OPPONENT = "liongis";
    private static final int HISTORY_GAMES = 611;
    private static final List<String> OPENERS = Arrays.asList("12Hatch", "12Pool", "4Pool", "9PoolSpeed", "Overpool");
    private static final Set<String> DORMANT = new HashSet<>(Arrays.asList("12Hatch", "12Pool", "9PoolSpeed"));
    private static final String[] MAPS = new String[14];

    static {
        for (int i = 0; i < MAPS.length; i++) {
            MAPS[i] = String.format("map%02d", i);
        }
    }

    private static final OutcomeModel ALWAYS_LOSS = (opener, gamesSince) -> false;
    private static final OutcomeModel ALWAYS_WIN = (opener, gamesSince) -> true;

    /**
     * From the locked-in state the policy must put a dormant opener into play within 20 games.
     * With the policy disabled the same harness must not.
     */
    @Test
    void dormantOpenerIsProbedWithinTwentyGamesWhenLeaderIsLosing() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        List<GameResult> enabled = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", 60, ALWAYS_LOSS, true);
        assertTrue(firstDormantGame(enabled) <= 20,
                "policy must probe a dormant opener within 20 games, measured " + firstDormantGame(enabled));

        List<GameResult> disabled = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", 120, ALWAYS_LOSS, false);
        int statusQuo = firstDormantGame(disabled);
        assertTrue(statusQuo > 20,
                "disabled harness must reproduce the status quo lock-in well beyond 20 games, measured " + statusQuo);
    }

    /**
     * While the leader's discounted win rate is above the gate, no probe may fire and no
     * dormant opener may take the slot.
     */
    @Test
    void winningLeaderIsNeverForcedToProbe() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        OpponentRecord record = buildSyntheticLiongisShape();
        for (int game = HISTORY_GAMES + 1; game <= HISTORY_GAMES + 60; game++) {
            appendGame(record, game % 2 == 1 ? "4Pool" : "Overpool", true, MAPS[game % MAPS.length]);
        }
        List<GameResult> results = runLoop(factory, record, "4Pool", 40, ALWAYS_WIN, true);
        assertTrue(results.stream().noneMatch(result -> result.probe),
                "a leader above the gate must never be overridden by a forced probe");
        assertTrue(results.stream().noneMatch(result -> DORMANT.contains(result.opener)),
                "a winning leader must keep the slot away from dormant openers");
    }

    /**
     * Over a long all-loss run, probe starts stay within the cooldown budget of one per
     * PROBE_COOLDOWN_GAMES, and the mechanism keeps re-firing rather than unlocking only once.
     */
    @Test
    void probeStartsRespectCooldownBudgetAndKeepFiring() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        List<GameResult> results = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", 200, ALWAYS_LOSS, true);
        long probes = results.stream().filter(result -> result.probe).count();
        assertTrue(probes <= 200 / LearningManager.PROBE_COOLDOWN_GAMES,
                "probe starts must stay within one per cooldown, measured " + probes + " in 200 games");
        assertTrue(probes >= 2, "the gate must re-fire under sustained failure, measured " + probes + " probes");
    }

    @Test
    void lowEvidenceExposureStaysWithinBudget() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        int games = 400;
        List<GameResult> results = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", games,
                ALWAYS_LOSS, true);
        long exposure = results.stream().filter(result -> DORMANT.contains(result.opener)).count();
        assertTrue(exposure <= games * LearningManager.PROBE_EXPOSURE_FRACTION,
                "low-evidence exposure exceeded its budget: " + exposure + " in " + games + " games");
        assertTrue(exposure > 0, "the exposure budget must still allow dormant probes");
    }

    /**
     * A probed opener that wins its re-entry game gets a PROBE_TRIAL_GAMES trial, then is
     * demoted and blocked until it goes dormant again. Incumbents are exempt from the cap.
     */
    @Test
    void probedOpenerThatWinsOnceIsCappedAtTrialGames() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        OutcomeModel reentryWin = (opener, gamesSince) -> DORMANT.contains(opener)
                && gamesSince >= LearningManager.PROBE_DORMANT_GAMES;
        List<GameResult> results = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", 200, reentryWin, true);
        for (String opener : OPENERS) {
            int longestStreak = maxConsecutiveGames(results, opener);
            assertTrue(!DORMANT.contains(opener) || longestStreak <= LearningManager.PROBE_TRIAL_GAMES,
                    opener + " held the slot for " + longestStreak + " consecutive games after re-entry");
        }
    }

    @Test
    void singleProbeWinCannotResumeAfterDemotion() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        OutcomeModel firstDormantWin = new OutcomeModel() {
            private boolean won;

            @Override
            public boolean isWin(String opener, int gamesSinceLastSelection) {
                if (!won && DORMANT.contains(opener)) {
                    won = true;
                    return true;
                }
                return false;
            }
        };
        List<GameResult> results = runLoop(factory, buildSyntheticLiongisShape(), "4Pool",
                LearningManager.PROBE_DORMANT_GAMES - 1, firstDormantWin, true);
        String probed = results.stream()
                .filter(result -> result.won)
                .findFirst()
                .get()
                .opener;
        long selections = results.stream().filter(result -> result.opener.equals(probed)).count();
        assertTrue(selections <= LearningManager.PROBE_BURST_GAMES,
                probed + " resumed after demotion and reached " + selections + " selections");
    }

    @Test
    void organicReentryDoesNotStarveAnotherDormantOpener() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        OpponentRecord record = buildSyntheticLiongisShape();
        appendGame(record, "12Pool", false, MAPS[record.getGameTimestamps().size() % MAPS.length]);
        List<GameResult> results = runLoop(factory, record, "12Pool", 60, ALWAYS_LOSS, true);
        assertTrue(results.stream().anyMatch(result -> result.opener.equals("12Hatch")),
                "an organic 12Pool re-entry must not starve 12Hatch");
    }

    /**
     * A probed opener that wins promotion becomes the new incumbent. Once it declines back
     * below the gate, probes must fire again against the other dormant openers.
     */
    @Test
    void gateRefiresAfterPromotedOpenerDeclines() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Zerg);
        OutcomeModel promoteTwelvePool = new OutcomeModel() {
            private int twelvePoolStreak;

            @Override
            public boolean isWin(String opener, int gamesSinceLastSelection) {
                if (!opener.equals("12Pool")) {
                    twelvePoolStreak = 0;
                    return false;
                }
                twelvePoolStreak++;
                return twelvePoolStreak <= LearningManager.PROBE_TRIAL_GAMES;
            }
        };
        List<GameResult> results = runLoop(factory, buildSyntheticLiongisShape(), "4Pool", 200, promoteTwelvePool, true);
        boolean refired = false;
        for (int i = 61; i < results.size(); i++) {
            GameResult result = results.get(i);
            refired = refired || result.probe && !result.opener.equals("12Pool");
        }
        assertTrue(refired,
                "after 12Pool's promotion and decline the gate must re-fire against other dormant openers");
    }

    private interface OutcomeModel {
        boolean isWin(String opener, int gamesSinceLastSelection);
    }

    private static final class GameResult {
        private final String opener;
        private final boolean probe;
        private final boolean won;

        private GameResult(String opener, boolean probe, boolean won) {
            this.opener = opener;
            this.probe = probe;
            this.won = won;
        }
    }

    private static OpponentRecord buildSyntheticLiongisShape() {
        Map<String, Record> openerRecords = new HashMap<>();
        for (String opener : OPENERS) {
            openerRecords.put(opener, Record.builder().opener(opener).wins(0).losses(0).build());
        }
        OpponentRecord record = OpponentRecord.builder()
                .name(OPPONENT)
                .race(Race.Zerg.toString())
                .wins(0)
                .losses(0)
                .openerRecord(openerRecords)
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
        Map<Integer, String> dormantAt = dormantPlacements();
        for (int game = 1; game <= HISTORY_GAMES; game++) {
            String opener = dormantAt.containsKey(game)
                    ? dormantAt.get(game)
                    : (game % 2 == 1 ? "4Pool" : "Overpool");
            boolean won = "4Pool".equals(opener) && game % 5 == 0
                    || "Overpool".equals(opener) && game % 8 == 0
                    || DORMANT.contains(opener) && game % 16 == 0;
            appendGame(record, opener, won, MAPS[game % MAPS.length]);
        }
        return record;
    }

    private static Map<Integer, String> dormantPlacements() {
        Map<Integer, String> dormantAt = new LinkedHashMap<>();
        placeDormant(dormantAt, "12Pool", 25, 222);
        placeDormant(dormantAt, "9PoolSpeed", 32, 306);
        placeDormant(dormantAt, "12Hatch", 20, 230);
        return dormantAt;
    }

    private static void placeDormant(Map<Integer, String> dormantAt, String opener, int count, int lastGame) {
        for (int k = 0; k < count; k++) {
            int position = (int) Math.floor(k * (lastGame - 1) / (double) (count - 1) + 0.5);
            while (dormantAt.containsKey(position)) {
                position++;
            }
            dormantAt.put(position, opener);
        }
    }

    private static String appendGame(OpponentRecord record, String opener, boolean won, String mapName) {
        long timestamp = record.getGameTimestamps().size() + 1;
        Record openerRecord = record.getOpenerRecord().get(opener);
        if (won) {
            openerRecord.setWins(openerRecord.getWins() + 1);
            openerRecord.addWinTimestamp(timestamp);
            record.setWins(record.getWins() + 1);
        } else {
            openerRecord.setLosses(openerRecord.getLosses() + 1);
            openerRecord.addLossTimestamp(timestamp);
            record.setLosses(record.getLosses() + 1);
        }
        String mapKey = WeightedUCBCalculator.createMapKey(mapName, opener);
        MapAwareRecord mapRecord = record.getMapSpecificOpenerRecord().get(mapKey);
        if (mapRecord == null) {
            mapRecord = MapAwareRecord.builder().strategy(opener).mapName(mapName).wins(0).losses(0).build();
            record.getMapSpecificOpenerRecord().put(mapKey, mapRecord);
        }
        if (won) {
            mapRecord.setWins(mapRecord.getWins() + 1);
            mapRecord.addWinTimestamp(timestamp);
        } else {
            mapRecord.setLosses(mapRecord.getLosses() + 1);
            mapRecord.addLossTimestamp(timestamp);
        }
        record.getGameTimestamps().add(timestamp);
        return opener;
    }

    private static List<GameResult> runLoop(BuildOrderFactory factory,
                                            OpponentRecord record,
                                            String lastGameOpener,
                                            int games,
                                            OutcomeModel outcome,
                                            boolean policyEnabled) {
        List<GameResult> results = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            String mapName = MAPS[record.getGameTimestamps().size() % MAPS.length];
            String natural = naturalWinner(factory, record, lastGameOpener, mapName);
            String selected = policyEnabled
                    ? LearningManager.selectOpenerName(null, factory, record, "", lastGameOpener, OPPONENT, mapName)
                    : natural;
            boolean probe = !selected.equals(natural) && !LearningManager.isDemotedBlocked(natural, record);
            boolean won = outcome.isWin(selected, gamesSinceSelection(record, selected));
            lastGameOpener = appendGame(record, selected, won, mapName);
            results.add(new GameResult(selected, probe, won));
        }
        return results;
    }

    private static String naturalWinner(BuildOrderFactory factory,
                                        OpponentRecord record,
                                        String lastGameOpener,
                                        String mapName) {
        List<String> playable = record.getOpenerRecord()
                .keySet()
                .stream()
                .filter(opener -> factory.isPlayableOpener(factory.getByName(opener)))
                .filter(opener -> !(lastGameOpener.equals("4Pool") && opener.equals("4Pool")))
                .collect(Collectors.toList());
        return WeightedUCBCalculator.findBestStrategy(playable, mapName, OPPONENT,
                record.getMapSpecificOpenerRecord(), record.getOpenerRecord(),
                record.totalGames(), record.getGameTimestamps());
    }

    private static int gamesSinceSelection(OpponentRecord record, String opener) {
        Record openerRecord = record.getOpenerRecord().get(opener);
        if (openerRecord == null || openerRecord.games() == 0) {
            return OpenerSelectionLog.NEVER_SELECTED;
        }
        return OpenerSelectionLog.from(openerRecord, record.getGameTimestamps(), LearningManager.PROBE_DORMANT_GAMES)
                .gamesSinceLastSelection();
    }

    private static int firstDormantGame(List<GameResult> results) {
        for (int i = 0; i < results.size(); i++) {
            if (DORMANT.contains(results.get(i).opener)) {
                return i + 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int maxConsecutiveGames(List<GameResult> results, String opener) {
        int longest = 0;
        int current = 0;
        for (GameResult result : results) {
            current = result.opener.equals(opener) ? current + 1 : 0;
            longest = Math.max(longest, current);
        }
        return longest;
    }
}
