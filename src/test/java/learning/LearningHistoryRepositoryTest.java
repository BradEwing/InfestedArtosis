package learning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LearningHistoryRepositoryTest {

    private static final String HEADER = "timestamp,is_winner,num_starting_locations,map_name,opponent_name,"
            + "opponent_race,opener,build_order,detected_strategies,frame_count";

    private static List<GameRecord> load(Path directory, String fileRace, String... rows) throws IOException {
        File read = directory.resolve("read.csv").toFile();
        StringBuilder content = new StringBuilder(HEADER).append('\n');
        for (String row : rows) {
            content.append(row).append('\n');
        }
        Files.write(read.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));
        File write = directory.resolve("write.csv").toFile();
        return new LearningHistoryRepository(read, write, fileRace).load().games();
    }

    @Test
    void aLegacySpeedlingRowLoadsAsTheVariantOfTheFilesRace(@TempDir Path directory) throws IOException {
        List<GameRecord> games = load(directory, "Terran",
                "1,true,4,(4)Python.scx,insanitybot,Terran,9Hatch,SpeedlingAllIn,2Gate,15000",
                "2,false,4,(4)Python.scx,insanitybot,Terran,12Hatch,3HatchLurker;SpeedlingAllIn,BunkerMain,16000");

        assertEquals("SpeedlingT", games.get(0).getBuildOrder());
        assertEquals("3HatchLurker;SpeedlingT", games.get(1).getBuildOrder());
        assertEquals("9Hatch", games.get(0).getOpener());
    }

    @Test
    void aRandomFilesRowMapsByTheRaceItResolvedTo(@TempDir Path directory) throws IOException {
        List<GameRecord> games = load(directory, "Unknown",
                "1,true,4,(4)Python.scx,Dave Churchill,Protoss,12Hatch,SpeedlingAllIn,2Gate,15000");

        assertEquals("SpeedlingP", games.get(0).getBuildOrder());
    }

    @Test
    void aLegacyRowOfAnUnknownRaceFileWithNoResolvedRaceKeepsItsNameAndMatchesNoBuild(@TempDir Path directory)
            throws IOException {
        List<GameRecord> games = load(directory, "Unknown",
                "1,true,4,(4)Python.scx,Dave Churchill,Unknown,12Pool,SpeedlingAllIn,,15000");

        assertEquals("SpeedlingAllIn", games.get(0).getBuildOrder());
        assertFalse(games.get(0).getBuildOrder().startsWith("SpeedlingR"));
    }

    @Test
    void currentNamesAndAMissingFileLoadUnchanged(@TempDir Path directory) throws IOException {
        List<GameRecord> games = load(directory, "Protoss",
                "1,true,4,(4)Python.scx,PurpleWave,Protoss,12Hatch,SpeedlingP,2Gate,15000");
        assertEquals("SpeedlingP", games.get(0).getBuildOrder());

        File absent = directory.resolve("absent.csv").toFile();
        assertEquals(0, new LearningHistoryRepository(absent, directory.resolve("w.csv").toFile(), "Zerg")
                .load().games().size());
    }
}
