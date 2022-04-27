package learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import strategy.StrategyRecord;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class LearningManager {
    private static String READ_DIR = "bwapi-data/read/";
    private static String WRITE_DIR = "bwapi-data/write/";

    private String opponentRace;
    private String opponentName;
    private String opponentFileName;

    private OpponentRecord opponentRecord = new OpponentRecord();
    private StrategyRecord currentOpponentStrategy; // Write this at end of game

    private ObjectMapper mapper = new ObjectMapper();

    public LearningManager(String opponentRace, String opponentName) {
        this.opponentRace = opponentRace;
        this.opponentName = opponentName;
        this.opponentFileName = opponentName + "_" + opponentRace + ".json";

        try {
            readOpponentRecord();
        } catch (IOException e) {
            // TODO: Create Opponent Record here
            System.out.printf("failed to read file! [%s]", e);
        }
    }

    public void onEnd(boolean isWinner) {
        if (isWinner) {
            currentOpponentStrategy.setWins(currentOpponentStrategy.getWins()+1);
        } else {
            currentOpponentStrategy.setLosses(currentOpponentStrategy.getLosses()+1);
        }
        Map<String, StrategyRecord> strategyRecords = opponentRecord.getOpponentStrategies();
        strategyRecords.put(currentOpponentStrategy.getStrategy(), currentOpponentStrategy);
        try {
            writeOpponentRecord();
        } catch (IOException e) {
            System.out.printf("failed to write file! [%s]", e);
        }
    }

    private void readOpponentRecord() throws IOException {
        File file = new File(READ_DIR + opponentFileName);
        if (!file.isFile()) {
            opponentRecord.setName(opponentName);
            opponentRecord.setRace(opponentRace);
            return;
        }

        InputStream inJson = OpponentRecord.class.getResourceAsStream(READ_DIR + opponentFileName);
        opponentRecord = mapper.readValue(inJson, OpponentRecord.class);
    }

    private void writeOpponentRecord() throws IOException {
        File file = new File(WRITE_DIR + opponentFileName);
        if (!file.isFile()) {
            return;
        }

        mapper.writeValue(file, opponentRecord);
    }

    private void ensureStrategies() {

    }

    private void determineStrategy() {

    }
}
