package learning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

final class LearningHistoryRepository {
    private static final String HEADER = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,"
            + "opponent_race,opener,build_order,detected_strategies,frame_count\n";

    private final File readFile;
    private final File writeFile;
    private final String fileRace;

    LearningHistoryRepository(String opponentFileName, String fileRace) {
        this.readFile = new File("bwapi-data/read/" + opponentFileName);
        this.writeFile = new File("bwapi-data/write/" + opponentFileName);
        this.fileRace = fileRace;
    }

    /**
     * Loads the games in the read file, with a legacy Speedling build order mapped to the variant of the
     * file's race or the row's resolved race, see {@link LegacyBuildOrderNames}. The file itself is not rewritten.
     */
    LearningHistory load() throws IOException {
        List<GameRecord> games = new ArrayList<>();
        if (!readFile.exists()) {
            return new LearningHistory(games);
        }
        List<String> lines = Files.readAllLines(readFile.toPath());
        for (int i = 1; i < lines.size(); i++) {
            GameRecord game = GameRecord.fromCsvRow(lines.get(i));
            String variantRace = LegacyBuildOrderNames.variantRace(fileRace, game.getOpponentRace());
            game.setBuildOrder(LegacyBuildOrderNames.resolveChain(game.getBuildOrder(), variantRace));
            game.setOpener(LegacyBuildOrderNames.resolve(game.getOpener(), variantRace));
            games.add(game);
        }
        return new LearningHistory(games);
    }

    void append(GameRecord game) throws IOException {
        initializeWriteFile();
        if (writeFile.isFile()) {
            Files.write(writeFile.toPath(), (game.toCsvRow() + "\n").getBytes(), StandardOpenOption.APPEND);
        }
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
