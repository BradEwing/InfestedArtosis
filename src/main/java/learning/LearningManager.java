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

    private static String READ_DIR = "bwapi-data/read/";
    private static String WRITE_DIR = "bwapi-data/write/";

    private Race opponentRace;
    private String opponentName;
    private String opponentFileName;

    private OpponentRecord opponentRecord;
    private Decisions decisions = new Decisions();
    private OpenerRecord currentOpener; // Write this at end of game

    private ObjectMapper mapper = new ObjectMapper();
    private BuildOrderFactory buildOrderFactory;

    public LearningManager(Config config, Race opponentRace, String opponentName, BWEM bwem) {
        this.config = config;
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";
        this.opponentRecord = new OpponentRecord(opponentName, opponentRace.toString(), 0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), new DefensiveSunkRecord(0 , 0));
        this.buildOrderFactory = new BuildOrderFactory(bwem.getMap().getStartingLocations().size(), opponentRace);

        try {
            readOpponentRecord();
        } catch (IOException e) {
            // Default to empty record
        }

        ensureOpenersInOpponentRecord();
        ensureDefensiveSunk();
        decisions.setOpener(determineOpener());
    }

    public void onEnd(boolean isWinner) {
        DefensiveSunkRecord defensiveSunkRecord = opponentRecord.getDefensiveSunkRecord();
        if (isWinner) {
            currentOpener.setWins(currentOpener.getWins()+1);
            opponentRecord.setWins(opponentRecord.getWins()+1);
            if (decisions.isDefensiveSunk()) {
                defensiveSunkRecord.setWins(defensiveSunkRecord.getWins()+1);
            }
        } else {
            currentOpener.setLosses(currentOpener.getLosses()+1);
            opponentRecord.setLosses(opponentRecord.getLosses()+1);
            if (decisions.isDefensiveSunk()) {
                defensiveSunkRecord.setLosses(defensiveSunkRecord.getLosses()+1);
            }
        }
        Map<String, OpenerRecord> openerRecords = opponentRecord.getOpenerRecord();
        openerRecords.put(currentOpener.getOpener(), currentOpener);
        try {
            writeOpponentRecord();
        } catch (IOException e) {
            //System.out.printf("failed to write file! [%s]", e);
        }
    }

    public Decisions getDecisions() { return decisions; }

    public OpponentRecord getOpponentRecord() { return this.opponentRecord; }

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
        if (opponentRecord.getOpenerRecord() == null) {
            opponentRecord.setOpenerRecord(new HashMap<>());
        }
    }

    private void writeOpponentRecord() throws IOException {
        File file = new File(WRITE_DIR + opponentFileName);
        file.createNewFile();
        if (!file.isFile()) {
            return;
        }

        mapper.writeValue(file, opponentRecord);
    }

    private void ensureOpenersInOpponentRecord() {
        Map<String, OpenerRecord> openerRecordMap = opponentRecord.getOpenerRecord();
        List<String> knownOpeners = new ArrayList<>(openerRecordMap
                .keySet());
        Set<String> missingOpeners = buildOrderFactory.getOpenerNames()
                .stream()
                .filter(s -> !knownOpeners.contains(s))
                .collect(Collectors.toSet());

        for (String opener: missingOpeners) {
            openerRecordMap.put(opener, new OpenerRecord(opener, 0, 0));
        }
    }

    private void ensureDefensiveSunk() {
        DefensiveSunkRecord record = opponentRecord.getDefensiveSunkRecord();
        if (record == null) {
            opponentRecord.setDefensiveSunkRecord(new DefensiveSunkRecord(0, 0));
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

        List<OpenerRecord> allRecords = opponentRecord.getOpenerRecord()
                .values()
                .stream()
                .filter(rec -> buildOrderFactory.isPlayableOpener(buildOrderFactory.getByName(rec.getOpener())))
                .sorted(new UCBRecordComparator(this.opponentRecord.totalGames()))
                .collect(Collectors.toList());

        currentOpener = allRecords.get(0);
        return buildOrderFactory.getByName(currentOpener.getOpener());
    }
}
