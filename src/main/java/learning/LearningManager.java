package learning;

import bwem.BWEM;
import com.fasterxml.jackson.databind.ObjectMapper;

import strategy.Strategy;
import strategy.StrategyFactory;

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
    private StrategyRecord currentOpponentStrategy; // Write this at end of game

    private ObjectMapper mapper = new ObjectMapper();

    private StrategyFactory strategyFactory;

    public LearningManager(String opponentRace, String opponentName, BWEM bwem) {
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";
        this.opponentRecord = new OpponentRecord(opponentName, opponentRace, 0, 0, new HashMap<>());
        this.bwem = bwem;

        this.strategyFactory = new StrategyFactory(bwem.getMap().getStartingLocations().size());

        try {
            readOpponentRecord();
        } catch (IOException e) {
        }

        ensureStrategiesInOpponentRecord();
        determineStrategy();
    }

    public void onEnd(boolean isWinner) {
        if (isWinner) {
            currentOpponentStrategy.setWins(currentOpponentStrategy.getWins()+1);
            opponentRecord.setWins(opponentRecord.getWins()+1);
        } else {
            currentOpponentStrategy.setLosses(currentOpponentStrategy.getLosses()+1);
            opponentRecord.setLosses(opponentRecord.getLosses()+1);
        }
        Map<String, StrategyRecord> strategyRecords = opponentRecord.getOpponentStrategies();
        strategyRecords.put(currentOpponentStrategy.getStrategy(), currentOpponentStrategy);
        try {
            writeOpponentRecord();
        } catch (IOException e) {
            //System.out.printf("failed to write file! [%s]", e);
        }
    }

    public Strategy getDeterminedStrategy() {
        return strategyFactory.getByName(currentOpponentStrategy.getStrategy());
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
    }

    private void writeOpponentRecord() throws IOException {
        File file = new File(WRITE_DIR + opponentFileName);
        file.createNewFile();
        if (!file.isFile()) {
            return;
        }

        mapper.writeValue(file, opponentRecord);
    }

    private void ensureStrategiesInOpponentRecord() {
        Map<String, StrategyRecord> strategyRecordMap = opponentRecord.getOpponentStrategies();
        List<String> knownStrategies = strategyRecordMap
                .keySet()
                .stream()
                .collect(Collectors.toList());
        Set<String> missingStrategies = strategyFactory.listAllStrategyNames()
                .stream()
                .filter(s -> !knownStrategies.contains(s))
                .collect(Collectors.toSet());

        for (String strategy: missingStrategies) {
            strategyRecordMap.put(strategy, new StrategyRecord(strategy, 0, 0));
        }
    }

    /**
     * Determine which strategy we should pick.
     *
     * Currently, pick the strategy we have the most net-wins on.
     *
     * May tweak this later
     */
    private void determineStrategy() {
        List<StrategyRecord> strategies = opponentRecord.getOpponentStrategies()
                .values()
                .stream()
                .filter(sr -> strategyFactory.getPlayableStrategies().contains(sr.getStrategy()))
                .collect(Collectors.toList());
        Collections.sort(strategies, new OpponentStrategyRecordComparator());
        currentOpponentStrategy = strategies.get(0);
    }
}
