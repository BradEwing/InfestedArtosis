package learning;

import bwem.BWEM;
import com.fasterxml.jackson.databind.ObjectMapper;

import strategy.openers.Opener;
import strategy.OpenerFactory;
import strategy.StrategyFactory;
import strategy.strategies.Strategy;

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
    private OpenerRecord currentOpener; // Write this at end of game
    private StrategyRecord currentStrategy;

    private ObjectMapper mapper = new ObjectMapper();

    private OpenerFactory openerFactory;
    private StrategyFactory strategyFactory;

    public LearningManager(String opponentRace, String opponentName, BWEM bwem) {
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";
        this.opponentRecord = new OpponentRecord(opponentName, opponentRace, 0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>());
        this.bwem = bwem;

        this.openerFactory = new OpenerFactory(bwem.getMap().getStartingLocations().size());
        this.strategyFactory = new StrategyFactory();

        try {
            readOpponentRecord();
        } catch (IOException e) {
        }

        ensureOpenersInOpponentRecord();
        ensureStrategiesInOpponentRecord();
        determineOpener();
        determineStrategy();
    }

    public void onEnd(boolean isWinner) {
        if (isWinner) {
            currentOpener.setWins(currentOpener.getWins()+1);
            opponentRecord.setWins(opponentRecord.getWins()+1);
            currentStrategy.setWins(currentStrategy.getWins()+1);
        } else {
            currentOpener.setLosses(currentOpener.getLosses()+1);
            opponentRecord.setLosses(opponentRecord.getLosses()+1);
            currentStrategy.setLosses(currentStrategy.getLosses()+1);
        }
        Map<String, OpenerRecord> openerRecords = opponentRecord.getOpenerRecord();
        openerRecords.put(currentOpener.getOpener(), currentOpener);
        Map<String, StrategyRecord> strategyRecordMap = opponentRecord.getStrategyRecordMap();
        strategyRecordMap.put(currentStrategy.getStrategy(), currentStrategy);
        try {
            writeOpponentRecord();
        } catch (IOException e) {
            //System.out.printf("failed to write file! [%s]", e);
        }
    }

    public Opener getDeterminedOpener() {
        return openerFactory.getByName(currentOpener.getOpener());
    }

    public Strategy getDeterminedStrategy() { return strategyFactory.getByName(currentStrategy.getStrategy()); }

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

    private void ensureStrategiesInOpponentRecord() {
        Map<String, StrategyRecord> strategyRecordMap = opponentRecord.getStrategyRecordMap();
        List<String> knownStrategies = strategyRecordMap
                .keySet()
                .stream()
                .collect(Collectors.toList());
        Set<String> missingStrategies = strategyFactory.listAllOpenerNames()
                .stream()
                .filter(s -> !knownStrategies.contains(s))
                .collect(Collectors.toSet());

        for (String strategy: missingStrategies) {
            strategyRecordMap.put(strategy, new StrategyRecord(strategy, 0, 0));
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
        List<OpenerRecord> openers = opponentRecord.getOpenerRecord()
                .values()
                .stream()
                .filter(sr -> openerFactory.getPlayableOpeners().contains(sr.getOpener()))
                .collect(Collectors.toList());
        Collections.sort(openers, new OpponentOpenerRecordComparator());
        currentOpener = openers.get(0);
    }

    private void determineStrategy() {
        List<StrategyRecord> strategies = opponentRecord.getStrategyRecordMap()
                .values()
                .stream()
                .filter(sr -> strategyFactory.getPlayableStrategies(getDeterminedOpener()).contains(sr.getStrategy()))
                .collect(Collectors.toList());
        Collections.sort(strategies, new OpponentStrategyRecordComparator());
        currentStrategy = strategies.get(0);
    }
}
