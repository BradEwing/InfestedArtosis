package util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BotLogger.
 *
 * <p>BotLogger is what makes a suppressed failure visible: without it the guard added in IA-239 would trade a JVM
 * crash for total silence. These tests pin the two behaviours that matter — a failure is reported with its stack
 * trace, and a failure that recurs every frame does not flood the log.
 */
public class BotLoggerTest {

    private PrintStream originalErr;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void setUp() throws UnsupportedEncodingException {
        originalErr = System.err;
        captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, "UTF-8"));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
    }

    private String captured() throws UnsupportedEncodingException {
        return captured.toString("UTF-8");
    }

    private Throwable thrownAt(String marker) {
        return new IllegalStateException(marker);
    }

    @Test
    void testLogsThrowableWithStackTrace() throws UnsupportedEncodingException {
        BotLogger.error("BotLoggerTest.stackTrace", thrownAt("testLogsThrowableWithStackTrace"));

        String output = captured();
        assertTrue(output.contains(BotLogger.PREFIX), "expected the marker prefix so logs can be scanned");
        assertTrue(output.contains("BotLoggerTest.stackTrace"), "expected the failing context");
        assertTrue(output.contains("IllegalStateException"), "expected the exception type");
        assertTrue(output.contains("testLogsThrowableWithStackTrace"), "expected the exception message");
        assertTrue(output.contains("at util.BotLoggerTest"), "expected a stack trace");
    }

    /**
     * A forced IOException in the learning write must produce a log line rather than the silence of an empty catch.
     */
    @Test
    void testLogsIoException() throws UnsupportedEncodingException {
        BotLogger.error("LearningManager.writeGameRecord", new IOException("testLogsIoException"));

        String output = captured();
        assertTrue(output.contains(BotLogger.PREFIX));
        assertTrue(output.contains("LearningManager.writeGameRecord"));
        assertTrue(output.contains("testLogsIoException"));
    }

    @Test
    void testLogsMessageWithoutThrowable() throws UnsupportedEncodingException {
        BotLogger.error("onEnd", "testLogsMessageWithoutThrowable");

        String output = captured();
        assertTrue(output.contains(BotLogger.PREFIX));
        assertTrue(output.contains("onEnd"));
        assertTrue(output.contains("testLogsMessageWithoutThrowable"));
    }

    /**
     * A failure inside onFrame recurs every frame. Only the first occurrence may print a stack trace, otherwise a
     * single bug buries the log under tens of thousands of identical traces.
     */
    @Test
    void testRepeatedFailureIsNotLoggedEveryTime() throws UnsupportedEncodingException {
        for (int i = 0; i < 50; i++) {
            BotLogger.error("BotLoggerTest.repeat", thrownAt("testRepeatedFailureIsNotLoggedEveryTime"));
        }

        String output = captured();
        assertTrue(output.contains("at util.BotLoggerTest"), "expected the first occurrence to carry a stack trace");
        assertEquals(1, countOccurrences(output, BotLogger.PREFIX), "expected exactly one reported occurrence");
    }

    @Test
    void testDistinctFailuresAreLoggedSeparately() throws UnsupportedEncodingException {
        BotLogger.error("onUnitDestroy", new IllegalStateException("testDistinctFailuresAreLoggedSeparately"));
        BotLogger.error("onUnitDestroy", new IllegalArgumentException("testDistinctFailuresAreLoggedSeparately"));

        String output = captured();
        assertTrue(output.contains("IllegalStateException"));
        assertTrue(output.contains("IllegalArgumentException"));
        assertFalse(output.contains("repeated"), "distinct failures are not repeats of one another");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
