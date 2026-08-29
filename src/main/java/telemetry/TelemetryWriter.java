package telemetry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only CSV file with an in-memory row buffer, so nothing touches the disk on the per-frame
 * hot path.
 *
 * <p>The bot must not write to stdout or stderr, and an exception escaping onFrame kills the JVM,
 * which the batch harness scores as a crash. The first failure therefore disables the writer
 * permanently rather than retrying: a write that failed once is overwhelmingly likely to keep
 * failing.
 */
final class TelemetryWriter {

    private static final String WRITE_DIR = "bwapi-data/write/";

    private final Path path;
    private final String header;
    private final List<String> buffer = new ArrayList<>();
    private boolean disabled;
    private boolean headerChecked;

    TelemetryWriter(String fileName, String header) {
        this(Paths.get(WRITE_DIR, fileName), header);
    }

    TelemetryWriter(Path path, String header) {
        this.path = path;
        this.header = header;
    }

    boolean isDisabled() {
        return disabled;
    }

    void append(String row) {
        if (disabled) {
            return;
        }
        buffer.add(row);
    }

    void append(List<String> rows) {
        if (disabled || rows.isEmpty()) {
            return;
        }
        buffer.addAll(rows);
        flush();
    }

    void flush() {
        if (disabled || buffer.isEmpty()) {
            return;
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            StringBuilder out = new StringBuilder();
            if (!headerChecked && !Files.exists(path)) {
                out.append(header).append('\n');
            }
            headerChecked = true;
            for (String row : buffer) {
                out.append(row).append('\n');
            }

            Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            buffer.clear();
        } catch (IOException | RuntimeException e) {
            disabled = true;
            buffer.clear();
        }
    }
}
