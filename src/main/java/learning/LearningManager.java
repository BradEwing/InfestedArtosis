package learning;

import bwapi.Game;
import bwapi.Race;
import bwem.BWEM;
import config.Config;
import info.GameState;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LearningManager {
    /**
     * Discounted win rate at or below which the incumbent opener counts as failing and a forced
     * re-probe of a dormant opener becomes allowed.
     */
    static final double PROBE_GATE_WIN_RATE = 0.30;

    /**
     * Games an opener must go unselected before it counts as dormant.
     */
    static final int PROBE_DORMANT_GAMES = 30;

    /**
     * Minimum age of the most recent dormant re-entry before another forced probe may start.
     */
    static final int PROBE_COOLDOWN_GAMES = 20;

    /**
     * Games a re-entered opener may play before its trial is judged.
     */
    static final int PROBE_TRIAL_GAMES = 3;

    /**
     * Trial wins that promote a re-entered opener back to unrestricted argmax eligibility.
     */
    static final int PROBE_PROMOTION_WINS = 2;

    static final double PROBE_LOW_EVIDENCE_GAMES = 3.0;

    static final double PROBE_EXPOSURE_FRACTION = 0.15;

    static final int PROBE_EXPOSURE_WINDOW_GAMES = 20;

    /**
     * Discounted win rate 4Pool must hold over the best alternative opener before it keeps the
     * slot instead of donating it to the bandit's next choice.
     */
    static final double REPEAT_DONATION_WIN_RATE = 0.25;

    /**
     * Discounted games 4Pool must hold before its lead over the best alternative opener counts.
     */
    static final double REPEAT_MIN_DISCOUNTED_GAMES = 5.0;

    private Config config;

    private Game game;
    private BWEM bwem;
    private GameState gameState;
    private Race opponentRace;
    private String opponentName;
    private String opponentFileName;

    private OpponentRecord opponentRecord;
    private Decisions decisions = new Decisions();
    private Record currentOpener;
    private Record activeBuildOrderRecord;
    private String lastGameDetectedStrategies = "";
    private String lastGameOpener = "";

    private BuildOrderFactory buildOrderFactory;
    private LearningHistoryRepository historyRepository;
    private LearningRecordAccumulator recordAccumulator;

    public LearningManager(Config config, Game game, BWEM bwem, GameState gameState) {
        this.config = config;
        this.game = game;
        this.bwem = bwem;
        this.gameState = gameState;
        this.opponentRace = game.enemy().getRace();
        this.opponentName = game.enemy().getName();
        this.opponentFileName = opponentName + "_" + opponentRace + ".csv";
        this.buildOrderFactory = new BuildOrderFactory(bwem.getMap().getStartingLocations().size(), opponentRace);
        this.historyRepository = new LearningHistoryRepository(opponentFileName);
        this.recordAccumulator = new LearningRecordAccumulator(opponentName, opponentRace);

        try {
            LearningHistory history = historyRepository.load();
            this.opponentRecord = recordAccumulator.reconstruct(history);
            GameRecord lastGame = history.lastGame();
            if (lastGame != null) {
                lastGameDetectedStrategies = lastGame.getDetectedStrategies();
                lastGameOpener = lastGame.getOpener() != null ? lastGame.getOpener() : "";
            }
        } catch (IOException e) {
            this.opponentRecord = recordAccumulator.reconstruct(new LearningHistory(new ArrayList<>()));
        }

        ensureOpenersInOpponentRecord();
        decisions.setOpener(determineOpener());
    }

    /**
     * Records the result of the finished game.
     */
    public void onEnd(boolean isWinner) {
        long currentTimestamp = System.currentTimeMillis();
        GameRecord gameRecord = createGameRecord(isWinner, currentTimestamp);
        recordAccumulator.apply(opponentRecord, gameRecord);
        
        try {
            historyRepository.append(gameRecord);
        } catch (IOException e) {
        }
    }

    /**
     * Returns the played opener's name, or an empty string if none was selected.
     */
    private String openerName() {
        return currentOpener != null ? currentOpener.getOpener() : "";
    }

    public Decisions getDecisions() {
        return decisions; 
    }

    public OpponentRecord getOpponentRecord() {
        return this.opponentRecord;
    }

    private GameRecord createGameRecord(boolean isWinner, long timestamp) {
        return GameRecord.builder()
            .timestamp(timestamp)
            .numStartingLocations(bwem.getMap().getStartingLocations().size())
            .mapName(game.mapFileName())
            .opponentName(opponentName)
            .opponentRace(gameState.getOpponentRace().toString())
            .opener(openerName())
            .buildOrder(activeBuildOrderRecord != null ? activeBuildOrderRecord.getOpener() : openerName())
            .detectedStrategies(gameState.getStrategyTracker() != null ?
                gameState.getStrategyTracker().getDetectedStrategiesAsString() : "")
            .isWinner(isWinner)
            .frameCount(game.getFrameCount())
            .build();
    }

    private void ensureOpenersInOpponentRecord() {
        Map<String, Record> openerRecordMap = opponentRecord.getOpenerRecord();
        if (openerRecordMap == null) {
            openerRecordMap = new HashMap<>();
            opponentRecord.setOpenerRecord(openerRecordMap);
        }
        
        for (String opener : buildOrderFactory.getOpenerNames()) {
            openerRecordMap.putIfAbsent(opener, Record.builder()
                .opener(opener)
                .wins(0)
                .losses(0)
                .build());
        }
        
        Map<String, Record> buildOrderRecordMap = opponentRecord.getBuildOrderRecord();
        if (buildOrderRecordMap == null) {
            buildOrderRecordMap = new HashMap<>();
            opponentRecord.setBuildOrderRecord(buildOrderRecordMap);
        }
        
        for (String buildOrder : buildOrderFactory.getPlayableNonOpenerNames()) {
            buildOrderRecordMap.putIfAbsent(buildOrder, Record.builder()
                .opener(buildOrder)
                .wins(0)
                .losses(0)
                .build());
        }
    }

    private BuildOrder determineOpener() {
        String openerName = selectOpenerName(config.openerOverride, buildOrderFactory, opponentRecord,
                lastGameDetectedStrategies, lastGameOpener, game.mapFileName());
        if (openerName == null) {
            return null;
        }

        currentOpener = opponentRecord.getOpenerRecord().get(openerName);
        return buildOrderFactory.getByName(openerName);
    }

    /**
     * Selects the opener by precedence: configured override, then the rush response, then
     * weighted D-UCB over the opponent's opener history.
     */
    static String selectOpenerName(String openerOverride,
                                   BuildOrderFactory buildOrderFactory,
                                   OpponentRecord opponentRecord,
                                   String lastGameDetectedStrategies,
                                   String lastGameOpener,
                                   String mapName) {
        return OpenerSelectionPolicy.select(openerOverride, buildOrderFactory, opponentRecord,
                lastGameDetectedStrategies, lastGameOpener, mapName);
    }

    /**
     * Returns whether 4Pool is barred from re-selection this game. The bar holds while 4Pool's
     * discounted record is too thin to trust, and while donating the slot is cheap: the best
     * alternative opener's discounted win rate stays within the repeat donation margin of
     * 4Pool's.
     */
    static boolean isBarredFromImmediateRepeat(String lastGameOpener,
                                               List<String> playableOpeners,
                                               OpponentRecord opponentRecord) {
        if (!"4Pool".equals(lastGameOpener) || !playableOpeners.contains(lastGameOpener)) {
            return false;
        }
        List<Long> gameTimestamps = opponentRecord.getGameTimestamps();
        Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
        Record fourPool = openerRecords.get(lastGameOpener);
        if (fourPool == null
                || fourPool.discountedGames(gameTimestamps) < REPEAT_MIN_DISCOUNTED_GAMES) {
            return true;
        }
        double fourPoolMean = fourPool.discountedMean(gameTimestamps);
        double bestAlternativeMean = 0.0;
        for (String opener : playableOpeners) {
            if (opener.equals(lastGameOpener)) {
                continue;
            }
            Record record = openerRecords.get(opener);
            if (record != null) {
                bestAlternativeMean = Math.max(bestAlternativeMean, record.discountedMean(gameTimestamps));
            }
        }
        return fourPoolMean - bestAlternativeMean <= REPEAT_DONATION_WIN_RATE;
    }

    /**
     * Applies the dormant re-probe policy to the UCB winner: replaces a benched winner with
     * the best selectable candidate, then may override the winner with a forced probe of a
     * dormant opener.
     */
    static String applyDormantReprobePolicy(String ucbWinner,
                                            List<String> playableOpeners,
                                            OpponentRecord opponentRecord,
                                            String mapName) {
        if (ucbWinner == null) {
            return null;
        }
        if (isBenched(ucbWinner, opponentRecord) || isExposureCapped(ucbWinner, opponentRecord)) {
            List<String> selectable = playableOpeners
                    .stream()
                    .filter(opener -> !isBenched(opener, opponentRecord))
                    .filter(opener -> !isExposureCapped(opener, opponentRecord))
                    .collect(Collectors.toList());
            if (selectable.isEmpty()) {
                return ucbWinner;
            }
            return WeightedUCBCalculator.findBestStrategy(
                selectable,
                mapName,
                opponentRecord.getMapSpecificOpenerRecord(),
                opponentRecord.getOpenerRecord(),
                opponentRecord.totalGames(),
                opponentRecord.getGameTimestamps()
            );
        }
        String probe = selectForcedReprobe(ucbWinner, playableOpeners, opponentRecord, mapName);
        return probe != null ? probe : ucbWinner;
    }

    /**
     * Returns an opener to force-probe, or null to keep the UCB winner. Probes only when the
     * UCB winner's discounted win rate is below the gate, no unproven trial is within the
     * re-entry cooldown, and some candidate has been unselected for at least the dormancy
     * horizon.
     */
    static String selectForcedReprobe(String ucbWinner,
                                      List<String> playableOpeners,
                                      OpponentRecord opponentRecord,
                                      String mapName) {
        Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
        List<Long> gameTimestamps = opponentRecord.getGameTimestamps();
        Record leader = openerRecords.get(ucbWinner);
        if (leader == null || leader.discountedMean(gameTimestamps) >= PROBE_GATE_WIN_RATE) {
            return null;
        }
        for (String opener : playableOpeners) {
            Record record = openerRecords.get(opener);
            if (record == null || record.games() == 0) {
                continue;
            }
            OpenerSelectionLog log = OpenerSelectionLog.from(record, gameTimestamps, PROBE_DORMANT_GAMES);
            if (log.isUnprovenTrial() && log.trialWins() < PROBE_PROMOTION_WINS
                    && log.reEntryAge() < PROBE_COOLDOWN_GAMES) {
                return null;
            }
        }
        if (recentUnprovenExposure(opponentRecord)
                >= Math.floor(PROBE_EXPOSURE_FRACTION * PROBE_EXPOSURE_WINDOW_GAMES)) {
            return null;
        }
        List<String> eligible = new ArrayList<>();
        for (String opener : playableOpeners) {
            if (opener.equals(ucbWinner)) {
                continue;
            }
            Record record = openerRecords.get(opener);
            if (record == null || record.games() == 0) {
                eligible.add(opener);
                continue;
            }
            OpenerSelectionLog log = OpenerSelectionLog.from(record, gameTimestamps, PROBE_DORMANT_GAMES);
            if (log.gamesSinceLastSelection() >= PROBE_DORMANT_GAMES) {
                eligible.add(opener);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String opener : eligible) {
            double score = WeightedUCBCalculator.calculateWeightedScore(opener, mapName,
                    opponentRecord.getMapSpecificOpenerRecord(), openerRecords,
                    opponentRecord.totalGames(), gameTimestamps);
            if (score > bestScore) {
                bestScore = score;
                best = opener;
            }
        }
        return best;
    }

    /**
     * Returns whether an unproven opener is benched after playing its full trial without
     * earning promotion.
     */
    static boolean isBenched(String opener, OpponentRecord opponentRecord) {
        Record record = opponentRecord.getOpenerRecord().get(opener);
        if (record == null || record.games() == 0) {
            return false;
        }
        OpenerSelectionLog log = OpenerSelectionLog.from(record,
                opponentRecord.getGameTimestamps(), PROBE_DORMANT_GAMES);
        return log.isUnprovenTrial()
                && log.trialCount() >= PROBE_TRIAL_GAMES
                && log.trialWins() < PROBE_PROMOTION_WINS;
    }

    /**
     * Returns whether an opener that would re-enter with low evidence is barred because the
     * recent window already holds the capped share of unproven trial games.
     */
    private static boolean isExposureCapped(String opener, OpponentRecord opponentRecord) {
        Record record = opponentRecord.getOpenerRecord().get(opener);
        if (record == null || record.games() == 0) {
            return false;
        }
        OpenerSelectionLog log = OpenerSelectionLog.from(record,
                opponentRecord.getGameTimestamps(), PROBE_DORMANT_GAMES);
        boolean wouldReenterWithLowEvidence = log.gamesSinceLastSelection() >= PROBE_DORMANT_GAMES
                && record.discountedGames(opponentRecord.getGameTimestamps())
                < PROBE_LOW_EVIDENCE_GAMES;
        return wouldReenterWithLowEvidence && recentUnprovenExposure(opponentRecord)
                >= Math.floor(PROBE_EXPOSURE_FRACTION * PROBE_EXPOSURE_WINDOW_GAMES);
    }

    /**
     * Returns how many games inside the exposure window were played by unproven trials that
     * have not yet earned promotion.
     */
    private static int recentUnprovenExposure(OpponentRecord opponentRecord) {
        int unprovenExposure = 0;
        for (Record record : opponentRecord.getOpenerRecord().values()) {
            if (record.games() == 0) {
                continue;
            }
            OpenerSelectionLog log = OpenerSelectionLog.from(record, opponentRecord.getGameTimestamps(),
                    PROBE_DORMANT_GAMES);
            if (log.isUnprovenTrial() && log.trialWins() < PROBE_PROMOTION_WINS) {
                unprovenExposure += log.trialGamesInExposureWindow();
            }
        }
        return unprovenExposure;
    }

    /**
     * Selects the build order to transition to from the given candidates using UCB.
     */
    public BuildOrder determineBuildOrder(Set<BuildOrder> candidates) {
        if (candidates.size() == 0) {
            return null;
        }

        for (BuildOrder candidate : candidates) {
            if (!opponentRecord.getBuildOrderRecord().containsKey(candidate.getName())) {
                opponentRecord.getBuildOrderRecord().put(candidate.getName(), Record.builder()
                    .opener(candidate.getName())
                    .wins(0)
                    .losses(0)
                    .build());
            }
        }

        if (config.strategyOverride != null) {
            BuildOrder forced = buildOrderFactory.getByName(config.strategyOverride);
            if (forced != null && candidates.contains(forced)) {
                activeBuildOrderRecord = opponentRecord.getBuildOrderRecord().get(forced.getName());
                return forced;
            }
        }
        
        if (candidates.size() == 1) {
            BuildOrder singleCandidate = candidates.iterator().next();
            activeBuildOrderRecord = opponentRecord.getBuildOrderRecord().get(singleCandidate.getName());
            return singleCandidate;
        }

        String currentMapName = game.mapFileName();
        
        List<String> candidateNames = candidates.stream()
                .map(BuildOrder::getName)
                .collect(Collectors.toList());
        
        String bestBuildOrder = WeightedUCBCalculator.findBestStrategy(
            candidateNames,
            currentMapName,
            opponentRecord.getMapSpecificBuildOrderRecord(),
            opponentRecord.getBuildOrderRecord(),
            this.opponentRecord.totalGames(),
            opponentRecord.getGameTimestamps()
        );
        
        activeBuildOrderRecord = opponentRecord.getBuildOrderRecord().get(bestBuildOrder);
        return buildOrderFactory.getByName(bestBuildOrder);
    }

}
