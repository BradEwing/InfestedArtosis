package telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatGameHeaderTest {

    @Test
    void goliathKillsAndMutaliskLossesAreAppendedAfterTheExistingColumns() {
        assertTrue(TelemetryLog.GAME_HEADER.endsWith(
                ",close_cooldown_frames,goliaths_killed,mutalisks_lost"));
    }
}
