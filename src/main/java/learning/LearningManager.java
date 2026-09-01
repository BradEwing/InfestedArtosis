package learning;

import bwapi.Game;
import bwapi.Race;
import bwem.BWEM;
import config.Config;
import info.GameState;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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

    private static String READ_DIR = "bwapi-data/read/";
    private static String WRITE_DIR = "bwapi-data/write/";

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

    public LearningManager(Config config, Game game, BWEM bwem, GameState gameState) {
        this.config = config;
        this.game = game;
        this.bwem = bwem;
        this.gameState = gameState;
        this.opponentRace = game.enemy().getRace();
        this.opponentName = game.enemy().getName();
        this.opponentFileName = opponentName + "_" + opponentRace + ".csv";
        this.opponentRecord = OpponentRecord.builder()
            .name(opponentName)
            .race(opponentRace.toString())
            .wins(0)
            .losses(0)
            .version(0)
            .openerRecord(new HashMap<>())
            .buildOrderRecord(new HashMap<>())
            .build();
        this.buildOrderFactory = new BuildOrderFactory(bwem.getMap().getStartingLocations().size(), opponentRace);

        try {
            readOpponentRecord();
        } catch (IOException e) {
        }

        ensureOpenersInOpponentRecord();
        decisions.setOpener(determineOpener());
    }

    /**
     * Records the result of the finished game.
     */
    public void onEnd(boolean isWinner) {
        long currentTimestamp = System.currentTimeMillis();

        if (currentOpener != null) {
            if (isWinner) {
                currentOpener.setWins(currentOpener.getWins() + 1);
                currentOpener.addWinTimestamp(currentTimestamp);
                opponentRecord.setWins(opponentRecord.getWins() + 1);
            } else {
                currentOpener.setLosses(currentOpener.getLosses() + 1);
                currentOpener.addLossTimestamp(currentTimestamp);
                opponentRecord.setLosses(opponentRecord.getLosses() + 1);
            }
            Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
            openerRecords.put(currentOpener.getOpener(), currentOpener);
            opponentRecord.getGameTimestamps().add(currentTimestamp);
        }

        if (activeBuildOrderRecord != null && !activeBuildOrderRecord.getOpener().equals(openerName())) {
            if (isWinner) {
                activeBuildOrderRecord.setWins(activeBuildOrderRecord.getWins() + 1);
                activeBuildOrderRecord.addWinTimestamp(currentTimestamp);
            } else {
                activeBuildOrderRecord.setLosses(activeBuildOrderRecord.getLosses() + 1);
                activeBuildOrderRecord.addLossTimestamp(currentTimestamp);
            }
            Map<String, Record> buildOrderRecords = opponentRecord.getBuildOrderRecord();
            buildOrderRecords.put(activeBuildOrderRecord.getOpener(), activeBuildOrderRecord);
        }
        
        try {
            writeGameRecord(isWinner);
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

    private void readOpponentRecord() throws IOException {
        File file = new File(READ_DIR + opponentFileName);
        if (!file.exists()) {
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace.toString());
            opponentRecord.ensureMapSpecificRecords();
            return;
        }

        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.size() <= 1) {
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace.toString());
            opponentRecord.ensureMapSpecificRecords();
            return;
        }

        opponentRecord.ensureMapSpecificRecords();

        for (int i = 1; i < lines.size(); i++) {
            GameRecord record = GameRecord.fromCsvRow(lines.get(i));
            opponentRecord.getGameTimestamps().add(record.getTimestamp());
            if (record.isWinner()) {
                opponentRecord.setWins(opponentRecord.getWins() + 1);
            } else {
                opponentRecord.setLosses(opponentRecord.getLosses() + 1);
            }
            
            Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
            if (openerRecords == null) {
                openerRecords = new HashMap<>();
                opponentRecord.setOpenerRecord(openerRecords);
            }
            
            Record openerRecord = openerRecords.get(record.getOpener());
            if (openerRecord == null) {
                openerRecord = Record.builder()
                    .opener(record.getOpener())
                    .wins(0)
                    .losses(0)
                    .build();
                openerRecords.put(record.getOpener(), openerRecord);
            }
            
            if (record.isWinner()) {
                openerRecord.setWins(openerRecord.getWins() + 1);
                openerRecord.addWinTimestamp(record.getTimestamp());
            } else {
                openerRecord.setLosses(openerRecord.getLosses() + 1);
                openerRecord.addLossTimestamp(record.getTimestamp());
            }
            
            String mapOpenerKey = WeightedUCBCalculator.createMapKey(record.getMapName(), record.getOpener());
            MapAwareRecord mapOpenerRecord = opponentRecord.getMapSpecificOpenerRecord().get(mapOpenerKey);
            if (mapOpenerRecord == null) {
                mapOpenerRecord = MapAwareRecord.builder()
                    .strategy(record.getOpener())
                    .mapName(record.getMapName())
                    .opponentName(opponentName)
                    .opponentRace(opponentRace.toString())
                    .wins(0)
                    .losses(0)
                    .build();
                opponentRecord.getMapSpecificOpenerRecord().put(mapOpenerKey, mapOpenerRecord);
            }
            
            if (record.isWinner()) {
                mapOpenerRecord.setWins(mapOpenerRecord.getWins() + 1);
                mapOpenerRecord.addWinTimestamp(record.getTimestamp());
            } else {
                mapOpenerRecord.setLosses(mapOpenerRecord.getLosses() + 1);
                mapOpenerRecord.addLossTimestamp(record.getTimestamp());
            }
            
            if (record.getBuildOrder() != null && !record.getBuildOrder().equals(record.getOpener())) {
                Map<String, Record> buildOrderRecords = opponentRecord.getBuildOrderRecord();
                if (buildOrderRecords == null) {
                    buildOrderRecords = new HashMap<>();
                    opponentRecord.setBuildOrderRecord(buildOrderRecords);
                }
                
                Record buildOrderRecord = buildOrderRecords.get(record.getBuildOrder());
                if (buildOrderRecord == null) {
                    buildOrderRecord = Record.builder()
                        .opener(record.getBuildOrder())
                        .wins(0)
                        .losses(0)
                        .build();
                    buildOrderRecords.put(record.getBuildOrder(), buildOrderRecord);
                }
                
                if (record.isWinner()) {
                    buildOrderRecord.setWins(buildOrderRecord.getWins() + 1);
                    buildOrderRecord.addWinTimestamp(record.getTimestamp());
                } else {
                    buildOrderRecord.setLosses(buildOrderRecord.getLosses() + 1);
                    buildOrderRecord.addLossTimestamp(record.getTimestamp());
                }
                
                String mapBuildOrderKey = WeightedUCBCalculator.createMapKey(record.getMapName(), record.getBuildOrder());
                MapAwareRecord mapBuildOrderRecord = opponentRecord.getMapSpecificBuildOrderRecord().get(mapBuildOrderKey);
                if (mapBuildOrderRecord == null) {
                    mapBuildOrderRecord = MapAwareRecord.builder()
                        .strategy(record.getBuildOrder())
                        .mapName(record.getMapName())
                        .opponentName(opponentName)
                        .opponentRace(opponentRace.toString())
                        .wins(0)
                        .losses(0)
                        .build();
                    opponentRecord.getMapSpecificBuildOrderRecord().put(mapBuildOrderKey, mapBuildOrderRecord);
                }
                
                if (record.isWinner()) {
                    mapBuildOrderRecord.setWins(mapBuildOrderRecord.getWins() + 1);
                    mapBuildOrderRecord.addWinTimestamp(record.getTimestamp());
                } else {
                    mapBuildOrderRecord.setLosses(mapBuildOrderRecord.getLosses() + 1);
                    mapBuildOrderRecord.addLossTimestamp(record.getTimestamp());
                }
            }
        }

        GameRecord lastRecord = GameRecord.fromCsvRow(lines.get(lines.size() - 1));
        lastGameDetectedStrategies = lastRecord.getDetectedStrategies();
        lastGameOpener = lastRecord.getOpener() != null ? lastRecord.getOpener() : "";
    }

    private void writeGameRecord(boolean isWinner) throws IOException {
        File readFile = new File(READ_DIR + opponentFileName);
        File writeFile = new File(WRITE_DIR + opponentFileName);
        
        if (!writeFile.exists()) {
            writeFile.createNewFile();
            String header = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,opponent_race,"
                + "opener,build_order,detected_strategies,frame_count\n";
            Files.write(writeFile.toPath(), header.getBytes(), StandardOpenOption.APPEND);
            
            if (readFile.exists() && readFile.isFile()) {
                List<String> readLines = Files.readAllLines(readFile.toPath());
                for (int i = 1; i < readLines.size(); i++) {
                    String dataRow = readLines.get(i) + "\n";
                    Files.write(writeFile.toPath(), dataRow.getBytes(), StandardOpenOption.APPEND);
                }
            }
        }
        
        if (!writeFile.isFile()) {
            return;
        }

        GameRecord gameRecord = GameRecord.builder()
            .timestamp(System.currentTimeMillis())
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

        String csvRow = gameRecord.toCsvRow() + "\n";
        Files.write(writeFile.toPath(), csvRow.getBytes(), StandardOpenOption.APPEND);
    }

    private void ensureOpenersInOpponentRecord() {
        Map<String, Record> openerRecordMap = opponentRecord.getOpenerRecord();
        if (openerRecordMap == null) {
            openerRecordMap = new HashMap<>();
            opponentRecord.setOpenerRecord(openerRecordMap);
        }
        
        List<String> knownOpeners = new ArrayList<>(openerRecordMap.keySet());
        Set<String> missingOpeners = buildOrderFactory.getOpenerNames()
                .stream()
                .filter(s -> !knownOpeners.contains(s))
                .collect(Collectors.toSet());

        for (String opener: missingOpeners) {
            openerRecordMap.put(opener, Record.builder()
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
        
        List<String> knownBuildOrders = new ArrayList<>(buildOrderRecordMap.keySet());
        Set<String> missingBuildOrders = buildOrderFactory.getPlayableNonOpenerNames()
                .stream()
                .filter(s -> !knownBuildOrders.contains(s))
                .collect(Collectors.toSet());

        for (String buildOrder: missingBuildOrders) {
            buildOrderRecordMap.put(buildOrder, Record.builder()
                .opener(buildOrder)
                .wins(0)
                .losses(0)
                .build());
        }
    }

    private BuildOrder determineOpener() {
        String openerName = selectOpenerName(config.openerOverride, buildOrderFactory, opponentRecord,
                lastGameDetectedStrategies, lastGameOpener, opponentName, game.mapFileName());
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
                                   String opponentName,
                                   String mapName) {
        if (openerOverride != null) {
            BuildOrder forced = buildOrderFactory.getByName(openerOverride);
            if (forced != null && buildOrderFactory.isPlayableOpener(forced)) {
                return forced.getName();
            }
        }

        boolean isRusher = lastGameDetectedStrategies.contains("CannonRush")
                || lastGameDetectedStrategies.contains("SCVRush");
        if (isRusher) {
            BuildOrder overpool = buildOrderFactory.getByName("Overpool");
            if (overpool != null && buildOrderFactory.isPlayableOpener(overpool)) {
                return overpool.getName();
            }
        }

        List<String> playableOpeners = opponentRecord.getOpenerRecord()
                .keySet()
                .stream()
                .filter(openerName -> buildOrderFactory.isPlayableOpener(buildOrderFactory.getByName(openerName)))
                .collect(Collectors.toList());

        if (isBarredFromImmediateRepeat(lastGameOpener, playableOpeners, opponentRecord)) {
            playableOpeners.removeIf(openerName -> openerName.equals(lastGameOpener));
        }

        if (playableOpeners.isEmpty()) {
            List<Record> allRecords = opponentRecord.getOpenerRecord()
                    .values()
                    .stream()
                    .filter(rec -> buildOrderFactory.isPlayableOpener(buildOrderFactory.getByName(rec.getOpener())))
                    .sorted(new UCBRecordComparator(opponentRecord.totalGames(), opponentRecord.getGameTimestamps()))
                    .collect(Collectors.toList());

            if (allRecords.isEmpty()) {
                return null;
            }

            return allRecords.get(0).getOpener();
        }

        String ucbWinner = WeightedUCBCalculator.findBestStrategy(
            playableOpeners,
            mapName,
            opponentName,
            opponentRecord.getMapSpecificOpenerRecord(),
            opponentRecord.getOpenerRecord(),
            opponentRecord.totalGames(),
            opponentRecord.getGameTimestamps()
        );
        return applyDormantReprobePolicy(ucbWinner, playableOpeners, opponentRecord, mapName, opponentName);
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
                                            String mapName,
                                            String opponentName) {
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
                opponentName,
                opponentRecord.getMapSpecificOpenerRecord(),
                opponentRecord.getOpenerRecord(),
                opponentRecord.totalGames(),
                opponentRecord.getGameTimestamps()
            );
        }
        String probe = selectForcedReprobe(ucbWinner, playableOpeners, opponentRecord, mapName, opponentName);
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
                                      String mapName,
                                      String opponentName) {
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
            double score = WeightedUCBCalculator.calculateWeightedScore(opener, mapName, opponentName,
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
            opponentName,
            opponentRecord.getMapSpecificBuildOrderRecord(),
            opponentRecord.getBuildOrderRecord(),
            this.opponentRecord.totalGames(),
            opponentRecord.getGameTimestamps()
        );
        
        if (bestBuildOrder != null) {
            activeBuildOrderRecord = opponentRecord.getBuildOrderRecord().get(bestBuildOrder);
            return buildOrderFactory.getByName(bestBuildOrder);
        }
        
        List<Record> allRecords = opponentRecord.getBuildOrderRecord()
                .values()
                .stream()
                .filter(rec -> {
                    BuildOrder buildOrder = buildOrderFactory.getByName(rec.getOpener());
                    return buildOrder != null && candidates.contains(buildOrder);
                })
                .sorted(new UCBRecordComparator(this.opponentRecord.totalGames(),
                        opponentRecord.getGameTimestamps()))
                .collect(Collectors.toList());

        if (allRecords.isEmpty()) {
            return null;
        }
        
        activeBuildOrderRecord = allRecords.get(0);
        return buildOrderFactory.getByName(activeBuildOrderRecord.getOpener());
    }

}
