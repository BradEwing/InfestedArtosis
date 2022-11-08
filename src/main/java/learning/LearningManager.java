package learning;

import bwem.BWEM;
import com.fasterxml.jackson.databind.ObjectMapper;

import strategy.Opener;
import strategy.OpenerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LearningManager {
    private BWEM bwem;

    private static String READ_DIR = "bwapi-data/read/";
    private static String WRITE_DIR = "bwapi-data/write/";

    private String opponentRace;
    private String opponentName;
    private String opponentFileName;

    private OpponentRecord opponentRecord;
    private OpenerRecord currentOpponentStrategy; // Write this at end of game

    private ObjectMapper mapper = new ObjectMapper();

    private OpenerFactory openerFactory;

    public LearningManager(String opponentRace, String opponentName, BWEM bwem) {
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";
        this.opponentRecord = new OpponentRecord(opponentName, opponentRace, 0, 0, new HashMap<>(), new HashMap<>());
        this.bwem = bwem;

        this.openerFactory = new OpenerFactory(bwem.getMap().getStartingLocations().size());

        try {
            readOpponentRecord();
        } catch (IOException e) {
        }

        ensureOpenersInOpponentRecord();
        determineOpener();
    }

    public void onEnd(boolean isWinner) {
        if (isWinner) {
            currentOpponentStrategy.setWins(currentOpponentStrategy.getWins()+1);
            opponentRecord.setWins(opponentRecord.getWins()+1);
        } else {
            currentOpponentStrategy.setLosses(currentOpponentStrategy.getLosses()+1);
            opponentRecord.setLosses(opponentRecord.getLosses()+1);
        }
        Map<String, OpenerRecord> openerRecords = opponentRecord.getOpenerRecord();
        openerRecords.put(currentOpponentStrategy.getStrategy(), currentOpponentStrategy);
        try {
            writeOpponentRecord();
        } catch (IOException e) {
            //System.out.printf("failed to write file! [%s]", e);
        }
    }

    public Opener getDeterminedStrategy() {
        return openerFactory.getByName(currentOpponentStrategy.getStrategy());
    }

    public OpponentRecord getOpponentRecord() { return this.opponentRecord; }

    private void readOpponentRecord() throws IOException {
        File file = new File(READ_DIR + opponentFileName);
        if (!file.exists()) {
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace);
            return;
        }

        InputStream inJson = new FileInputStream(file);
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
        List<String> knownOpeners = openerRecordMap
                .keySet()
                .stream()
                .collect(Collectors.toList());
        Set<String> missingOpeners = openerFactory.listAllOpenerNames()
                .stream()
                .filter(s -> !knownOpeners.contains(s))
                .collect(Collectors.toSet());

        for (String opener: missingOpeners) {
            openerRecordMap.put(opener, new OpenerRecord(opener, 0, 0));
        }
    }

    /**
     * Determine which opener we should pick.
     *
     * Currently, pick the opener we have the most net-wins on.
     *
     * May tweak this later
     */
    private void determineOpener() {
        List<OpenerRecord> strategies = opponentRecord.getOpenerRecord()
                .values()
                .stream()
                .filter(sr -> openerFactory.getPlayableOpeners().contains(sr.getStrategy()))
                .collect(Collectors.toList());
        Collections.sort(strategies, new OpponentOpenerRecordComparator());
        currentOpponentStrategy = strategies.get(0);
    }
}
