package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StalemateRecorderTest {
    @TempDir
    File dir;

    private File readFile;
    private File writeFile;
    private StalemateRecorder recorder;

    @BeforeEach
    void setUp() {
        readFile = new File(dir, "read.csv");
        writeFile = new File(dir, "write.csv");
        recorder = new StalemateRecorder(new LearningHistoryRepository(readFile, writeFile));
    }

    private static GameRecord row(long timestamp, boolean winner, int frame, boolean stalemate) {
        return GameRecord.builder()
                .timestamp(timestamp)
                .isWinner(winner)
                .numStartingLocations(4)
                .mapName("(4)Map.scx")
                .opponentName("Pylon Puller")
                .opponentRace("Protoss")
                .opener("3HatchBeforePool")
                .buildOrder("SpeedlingP")
                .detectedStrategies("2Gate")
                .frameCount(frame)
                .stalemate(stalemate)
                .build();
    }

    private List<String> lines() throws IOException {
        return Files.readAllLines(writeFile.toPath());
    }

    @Test
    void writesNothingBeforeTheFrameCap() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME - 1, () -> row(1, false, StalemateRecorder.FRAME - 1, true));

        assertFalse(writeFile.exists());
        assertFalse(recorder.hasWritten());
    }

    @Test
    void writesOneMarkedLossRowAtTheFrameCap() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME, () -> row(1, false, StalemateRecorder.FRAME, true));

        List<String> lines = lines();
        assertEquals(2, lines.size());
        GameRecord parsed = GameRecord.fromCsvRow(lines.get(1));
        assertTrue(parsed.isStalemate());
        assertFalse(parsed.isWinner());
        assertEquals("3HatchBeforePool", parsed.getOpener());
        assertEquals("SpeedlingP", parsed.getBuildOrder());
    }

    @Test
    void writesTheRowOnlyOnceAcrossLaterFrames() throws IOException {
        AtomicInteger supplied = new AtomicInteger();
        for (int frame = StalemateRecorder.FRAME; frame < StalemateRecorder.FRAME + 500; frame++) {
            int f = frame;
            recorder.onFrame(f, () -> {
                supplied.incrementAndGet();
                return row(1, false, f, true);
            });
        }

        assertEquals(1, supplied.get());
        assertEquals(2, lines().size());
    }

    @Test
    void realLossAfterTheStalemateRowReplacesIt() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME, () -> row(1, false, StalemateRecorder.FRAME, true));
        recorder.onEnd(row(2, false, 92692, false));

        List<String> lines = lines();
        assertEquals(2, lines.size());
        GameRecord parsed = GameRecord.fromCsvRow(lines.get(1));
        assertFalse(parsed.isStalemate());
        assertEquals(92692, parsed.getFrameCount());
    }

    @Test
    void realWinAfterTheStalemateRowReplacesIt() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME, () -> row(1, false, StalemateRecorder.FRAME, true));
        recorder.onEnd(row(2, true, 90000, false));

        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertTrue(GameRecord.fromCsvRow(lines.get(1)).isWinner());
    }

    @Test
    void normalGameWithNoStalemateRowAppendsOneUnmarkedRow() throws IOException {
        GameRecord result = row(2, true, 12154, false);
        recorder.onEnd(result);

        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertEquals(result.toCsvRow(), lines.get(1));
        assertFalse(lines.get(1).contains(GameRecord.STALEMATE_REASON));
    }

    @Test
    void stalemateReplacementKeepsEarlierHistoryRows() throws IOException {
        String history = "1,true,4,(4)Map.scx,Pylon Puller,Protoss,9PoolSpeed,9PoolSpeed,,5000";
        Files.write(readFile.toPath(), ("timestamp,is_winner\n" + history + "\n").getBytes());

        recorder.onFrame(StalemateRecorder.FRAME, () -> row(5, false, StalemateRecorder.FRAME, true));
        recorder.onEnd(row(6, true, 91000, false));

        List<String> lines = lines();
        assertEquals(3, lines.size());
        assertEquals(history, lines.get(1));
        assertTrue(GameRecord.fromCsvRow(lines.get(2)).isWinner());
    }

    @Test
    void failedWriteIsNotRetriedOnLaterFrames() {
        File missingDir = new File(dir, "missing");
        StalemateRecorder failing = new StalemateRecorder(
                new LearningHistoryRepository(readFile, new File(missingDir, "write.csv")));
        AtomicInteger supplied = new AtomicInteger();

        for (int frame = StalemateRecorder.FRAME; frame < StalemateRecorder.FRAME + 50; frame++) {
            failing.onFrame(frame, () -> {
                supplied.incrementAndGet();
                return row(1, false, StalemateRecorder.FRAME, true);
            });
        }

        assertEquals(1, supplied.get());
        assertFalse(failing.hasWritten());
    }

    @Test
    void resultAppendsWhenTheLastRowIsNotTheStalemateRow() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME, () -> row(1, false, StalemateRecorder.FRAME, true));
        Files.write(writeFile.toPath(), "9,true,4,(4)Map.scx,Opp,Terran,A,A,,100\n".getBytes(),
                java.nio.file.StandardOpenOption.APPEND);

        recorder.onEnd(row(2, true, 90000, false));

        assertEquals(4, lines().size());
        assertTrue(GameRecord.fromCsvRow(lines().get(3)).isWinner());
    }

    @Test
    void headerCarriesTheReasonColumnAndStalemateRowLoadsFromRead() throws IOException {
        recorder.onFrame(StalemateRecorder.FRAME, () -> row(1, false, StalemateRecorder.FRAME, true));

        assertTrue(lines().get(0).endsWith(",frame_count,reason"));
        Files.copy(writeFile.toPath(), readFile.toPath());
        LearningHistory history = new LearningHistoryRepository(readFile, new File(dir, "other.csv")).load();
        assertEquals(1, history.games().size());
        assertTrue(history.games().get(0).isStalemate());
        assertFalse(history.games().get(0).isWinner());
    }
}
