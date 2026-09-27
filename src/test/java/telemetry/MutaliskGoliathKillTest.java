package telemetry;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutaliskGoliathKillTest {

    private static final int GOLIATH_ID = 412;
    private static final int BEFORE_WINDOW = CombatTelemetry.MUTALISK_KILL_WINDOW.getFrames() - 1;
    private static final List<Integer> TARGETED = Arrays.asList(97, GOLIATH_ID);

    @Test
    void anEnemyGoliathAMutaliskTargetsCountsBeforeTwelveMinutes() {
        assertTrue(CombatTelemetry.countsAsMutaliskGoliathKill(UnitType.Terran_Goliath, true, BEFORE_WINDOW,
                GOLIATH_ID, TARGETED));
    }

    @Test
    void aGoliathDeathFromTwelveMinutesOnDoesNotCount() {
        assertFalse(CombatTelemetry.countsAsMutaliskGoliathKill(UnitType.Terran_Goliath, true, BEFORE_WINDOW + 1,
                GOLIATH_ID, TARGETED));
    }

    @Test
    void aGoliathNoMutaliskTargetsDoesNotCount() {
        assertFalse(CombatTelemetry.countsAsMutaliskGoliathKill(UnitType.Terran_Goliath, true, BEFORE_WINDOW,
                GOLIATH_ID, Collections.singletonList(97)));
    }

    @Test
    void otherTypesAndNonEnemyUnitsDoNotCount() {
        assertFalse(CombatTelemetry.countsAsMutaliskGoliathKill(UnitType.Terran_Bunker, true, BEFORE_WINDOW,
                GOLIATH_ID, TARGETED));
        assertFalse(CombatTelemetry.countsAsMutaliskGoliathKill(UnitType.Terran_Goliath, false, BEFORE_WINDOW,
                GOLIATH_ID, TARGETED));
    }
}
