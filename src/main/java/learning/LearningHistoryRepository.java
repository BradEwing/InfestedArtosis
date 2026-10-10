package learning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class LearningHistoryRepository {
    private static final String HEADER = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,"
            + "opponent_race,opener,build_order,detected_strategies,frame_count,reason\n";

    private final File readFile;
    private final File writeFile;

    LearningHistoryRepository(String opponentFileName) {
        this(new File("bwapi-data/read/" + opponentFileName), new File("bwapi-data/write/" + opponentFileName));
    }

    LearningHistoryRepository(File readFile, File writeFile) {
        this.readFile = readFile;
        this.writeFile = writeFile;
    }

    LearningHistory load() throws IOException {
        List<GameRecord> games = new ArrayList<>();
        if (!readFile.exists()) {
            return new LearningHistory(games);
        }
        List<String> lines = Files.readAllLines(readFile.toPath());
        for (int i = 1; i < lines.size(); i++) {
            games.add(GameRecord.fromCsvRow(lines.get(i)));
        }
        return new LearningHistory(games);
    }

    void append(GameRecord game) throws IOException {
        initializeWriteFile();
        if (writeFile.isFile()) {
            Files.write(writeFile.toPath(), (game.toCsvRow() + "\n").getBytes(), StandardOpenOption.APPEND);
        }
    }

    /**
     * Replaces the last row of the write file with the given game when that row is the previous game's, and
     * appends the game otherwise, so one game never leaves two rows.
     */
    void replaceLast(GameRecord previous, GameRecord game) throws IOException {
        initializeWriteFile();
        if (!writeFile.isFile()) {
            return;
        }
        List<String> lines = new ArrayList<>(Files.readAllLines(writeFile.toPath()));
        int last = lines.size() - 1;
        if (last < 1 || !lines.get(last).equals(previous.toCsvRow())) {
            append(game);
            return;
        }
        lines.set(last, game.toCsvRow());
        Files.write(writeFile.toPath(), (String.join("\n", lines) + "\n").getBytes());
    }

    private void initializeWriteFile() throws IOException {
        if (writeFile.exists()) {
            return;
        }
        writeFile.createNewFile();
        Files.write(writeFile.toPath(), HEADER.getBytes(), StandardOpenOption.APPEND);
        if (!readFile.isFile()) {
            return;
        }
        List<String> readLines = Files.readAllLines(readFile.toPath());
        for (int i = 1; i < readLines.size(); i++) {
            Files.write(writeFile.toPath(), (readLines.get(i) + "\n").getBytes(), StandardOpenOption.APPEND);
        }
    }
}
