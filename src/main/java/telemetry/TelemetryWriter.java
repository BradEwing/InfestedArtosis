package telemetry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Appends CSV rows to a file under the bot's write directory.
 *
 * <p>The bot must not write to stdout or stderr, and an escaped exception inside onFrame is scored
 * as a crash by the batch harness, so every failure is swallowed and disables the writer for the
 * rest of the game rather than being retried.
 */
class TelemetryWriter {

    private static final String WRITE_DIR = "bwapi-data/write/";

    private final Path path;
    private final String header;
    private boolean disabled;
    private boolean headerWritten;

    TelemetryWriter(String fileName, String header) {
        this.path = Paths.get(WRITE_DIR, fileName);
        this.header = header;
    }

    boolean isDisabled() {
        return disabled;
    }

    void append(List<String> rows) {
        if (disabled || rows.isEmpty()) {
            return;
        }

        try {
            StringBuilder payload = new StringBuilder();
            if (!headerWritten) {
                Files.createDirectories(path.getParent());
                payload.append(header).append('\n');
            }
            for (String row : rows) {
                payload.append(row).append('\n');
            }
            Files.write(path, payload.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            headerWritten = true;
        } catch (Exception e) {
            disabled = true;
        }
    }
}
