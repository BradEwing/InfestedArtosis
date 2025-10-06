package learning;

import bwapi.Race;
import bwem.BWEM;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.Config;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LearningManager {
    private Config config;
    final private int recordVersion = 3;

    private static String READ_DIR = "bwapi-data/read/";
    private static String WRITE_DIR = "bwapi-data/write/";

    private Race opponentRace;
    private String opponentName;
    private String opponentFileName;

    private OpponentRecord opponentRecord;
    private Decisions decisions = new Decisions();
    private Record currentOpener; // Write this at end of game
    private Record activeBuildOrderRecord; // Track current non-opener strategy

    private ObjectMapper mapper = new ObjectMapper();
    private BuildOrderFactory buildOrderFactory;

    public LearningManager(Config config, Race opponentRace, String opponentName, BWEM bwem) {
        this.config = config;
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";
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
            writeOpponentRecord();
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

        InputStream inJson = Files.newInputStream(file.toPath());
        opponentRecord = mapper.readValue(inJson, OpponentRecord.class);

        // Handle new change to JSON file
        if (opponentRecord.getOpenerRecord() == null || opponentRecord.getVersion() != recordVersion) {
            opponentRecord.setOpenerRecord(new HashMap<>());
            opponentRecord.setBuildOrderRecord(new HashMap<>());
            opponentRecord.setWins(0);
            opponentRecord.setLosses(0);
        }
    }

    private void writeOpponentRecord() throws IOException {
        File file = new File(WRITE_DIR + opponentFileName);
        file.createNewFile();
        if (!file.isFile()) {
            return;
        }

        opponentRecord.setVersion(recordVersion);
        mapper.writeValue(file, opponentRecord);
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
