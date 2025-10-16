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
    private Record currentOpener; // Write this at end of game
    private Record activeBuildOrderRecord; // Track current non-opener strategy

    private BuildOrderFactory buildOrderFactory;

    public LearningManager(Config config, Game game, BWEM bwem, GameState gameState) {
        this.config = config;
        this.game = game;
        this.bwem = bwem;
        this.gameState = gameState;
        this.opponentRace = game.enemy().getRace();
        this.opponentName = game.enemy().getName();
        this.opponentFileName = opponentName + "_" + opponentRace + ".csv";
        this.opponentRecord = new OpponentRecord(opponentName, opponentRace.toString(), 0, 0, 0, new HashMap<>(), new HashMap<>());
        this.buildOrderFactory = new BuildOrderFactory(bwem.getMap().getStartingLocations().size(), opponentRace);

        try {
            readOpponentRecord();
        } catch (IOException e) {
            // Default to empty record
        }

        ensureOpenersInOpponentRecord();
        decisions.setOpener(determineOpener());
    }

    public void onEnd(boolean isWinner) {
        if (isWinner) {
            currentOpener.setWins(currentOpener.getWins()+1);
            opponentRecord.setWins(opponentRecord.getWins()+1);
        } else {
            currentOpener.setLosses(currentOpener.getLosses()+1);
            opponentRecord.setLosses(opponentRecord.getLosses()+1);
        }
        Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
        openerRecords.put(currentOpener.getOpener(), currentOpener);
        
        // Also track the active BuildOrder if it exists and is different from opener
        if (activeBuildOrderRecord != null && !activeBuildOrderRecord.getOpener().equals(currentOpener.getOpener())) {
            if (isWinner) {
                activeBuildOrderRecord.setWins(activeBuildOrderRecord.getWins()+1);
            } else {
                activeBuildOrderRecord.setLosses(activeBuildOrderRecord.getLosses()+1);
            }
            Map<String, Record> buildOrderRecords = opponentRecord.getBuildOrderRecord();
            buildOrderRecords.put(activeBuildOrderRecord.getOpener(), activeBuildOrderRecord);
        }
        
        try {
            writeGameRecord(isWinner);
        } catch (IOException e) {
            //System.out.printf("failed to write file! [%s]", e);
        }
    }

    public Decisions getDecisions() { return decisions; }

    public OpponentRecord getOpponentRecord() {
        return this.opponentRecord;
    }

    private void readOpponentRecord() throws IOException {
        File file = new File(READ_DIR + opponentFileName);
        if (!file.exists()) {
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace.toString());
            return;
        }

        // Read CSV file and aggregate stats for UCB algorithm compatibility
        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.size() <= 1) { // Only header or empty
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace.toString());
            return;
        }

        // Skip header line
        for (int i = 1; i < lines.size(); i++) {
            GameRecord record = GameRecord.fromCsvRow(lines.get(i));
            if (record.isWinner()) {
                opponentRecord.setWins(opponentRecord.getWins() + 1);
            } else {
                opponentRecord.setLosses(opponentRecord.getLosses() + 1);
            }
            
            // Aggregate opener stats
            Map<String, Record> openerRecords = opponentRecord.getOpenerRecord();
            if (openerRecords == null) {
                openerRecords = new HashMap<>();
                opponentRecord.setOpenerRecord(openerRecords);
            }
            
            Record openerRecord = openerRecords.get(record.getOpener());
            if (openerRecord == null) {
                openerRecord = new Record(record.getOpener(), 0, 0);
                openerRecords.put(record.getOpener(), openerRecord);
            }
            
            if (record.isWinner()) {
                openerRecord.setWins(openerRecord.getWins() + 1);
            } else {
                openerRecord.setLosses(openerRecord.getLosses() + 1);
            }
            
            // Aggregate build order stats if different from opener
            if (record.getBuildOrder() != null && !record.getBuildOrder().equals(record.getOpener())) {
                Map<String, Record> buildOrderRecords = opponentRecord.getBuildOrderRecord();
                if (buildOrderRecords == null) {
                    buildOrderRecords = new HashMap<>();
                    opponentRecord.setBuildOrderRecord(buildOrderRecords);
                }
                
                Record buildOrderRecord = buildOrderRecords.get(record.getBuildOrder());
                if (buildOrderRecord == null) {
                    buildOrderRecord = new Record(record.getBuildOrder(), 0, 0);
                    buildOrderRecords.put(record.getBuildOrder(), buildOrderRecord);
                }
                
                if (record.isWinner()) {
                    buildOrderRecord.setWins(buildOrderRecord.getWins() + 1);
                } else {
                    buildOrderRecord.setLosses(buildOrderRecord.getLosses() + 1);
                }
            }
        }
    }

    private void writeGameRecord(boolean isWinner) throws IOException {
        File readFile = new File(READ_DIR + opponentFileName);
        File writeFile = new File(WRITE_DIR + opponentFileName);
        
        // Create write file and copy existing data from read file if it exists
        if (!writeFile.exists()) {
            writeFile.createNewFile();
            String header = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,opponent_race,opener,build_order,detected_strategies\n";
            Files.write(writeFile.toPath(), header.getBytes(), StandardOpenOption.APPEND);
            
            // Copy all existing records from read file if it exists
            if (readFile.exists() && readFile.isFile()) {
                List<String> readLines = Files.readAllLines(readFile.toPath());
                // Skip header line and copy all data rows
                for (int i = 1; i < readLines.size(); i++) {
                    String dataRow = readLines.get(i) + "\n";
                    Files.write(writeFile.toPath(), dataRow.getBytes(), StandardOpenOption.APPEND);
                }
            }
        }
        
        if (!writeFile.isFile()) {
            return;
        }

        // Create game record
        GameRecord gameRecord = GameRecord.builder()
            .timestamp(System.currentTimeMillis())
            .numStartingLocations(bwem.getMap().getStartingLocations().size())
            .mapName(game.mapFileName())
            .opponentName(opponentName)
            .opponentRace(opponentRace.toString())
            .opener(currentOpener.getOpener())
            .buildOrder(activeBuildOrderRecord != null ? activeBuildOrderRecord.getOpener() : currentOpener.getOpener())
            .detectedStrategies(gameState.getStrategyTracker() != null ? 
                gameState.getStrategyTracker().getDetectedStrategiesAsString() : "")
            .isWinner(isWinner)
            .build();

        // Append new record
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
            openerRecordMap.put(opener, new Record(opener, 0, 0));
        }
        
        // Initialize build order records (race-specific strategies)
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
            buildOrderRecordMap.put(buildOrder, new Record(buildOrder, 0, 0));
        }
    }

    /**
     * Determine which opener we should pick using a UCB algorithm.
     */
    private BuildOrder determineOpener() {
        if (config.openerOverride != null) {
            BuildOrder forced = buildOrderFactory.getByName(config.openerOverride);
            if (forced != null && buildOrderFactory.isPlayableOpener(forced)) {
                currentOpener = opponentRecord.getOpenerRecord().get(forced.getName());
                return forced;
            }
        }

        List<Record> allRecords = opponentRecord.getOpenerRecord()
                .values()
                .stream()
                .filter(rec -> buildOrderFactory.isPlayableOpener(buildOrderFactory.getByName(rec.getOpener())))
                .sorted(new UCBRecordComparator(this.opponentRecord.totalGames()))
                .collect(Collectors.toList());

        currentOpener = allRecords.get(0);
        return buildOrderFactory.getByName(currentOpener.getOpener());
    }

    /**
     * Determine which BuildOrder to transition to using UCB algorithm.
     * If only one candidate, return it directly. If multiple candidates, use UCB evaluation.
     */
    public BuildOrder determineBuildOrder(Set<BuildOrder> candidates) {
        if (candidates.size() == 0) {
            return null;
        }

        // Ensure records exist for all candidate build orders
        for (BuildOrder candidate : candidates) {
            if (!opponentRecord.getBuildOrderRecord().containsKey(candidate.getName())) {
                opponentRecord.getBuildOrderRecord().put(candidate.getName(), new Record(candidate.getName(), 0, 0));
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

        List<Record> allRecords = opponentRecord.getBuildOrderRecord()
                .values()
                .stream()
                .filter(rec -> {
                    BuildOrder buildOrder = buildOrderFactory.getByName(rec.getOpener());
                    return buildOrder != null && candidates.contains(buildOrder);
                })
                .sorted(new UCBRecordComparator(this.opponentRecord.totalGames()))
                .collect(Collectors.toList());

        activeBuildOrderRecord = allRecords.get(0);
        return buildOrderFactory.getByName(activeBuildOrderRecord.getOpener());
    }

}
