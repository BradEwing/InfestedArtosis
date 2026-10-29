package telemetry;

import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Evidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedParityLoggerTest {

    private static int columns(String csv) {
        return csv.split(",", -1).length;
    }

    @Test
    void aGateRowHasOneCellPerHeaderColumn() {
        SpeedParityGate gate = new SpeedParityGate("squad-1", "SPEED_PARITY", true, "NONE", "PLAUSIBLE", 12, 24, 4,
                30);

        String row = SpeedParityLogger.gateRow("game", 4000, gate);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,4000,GATE,squad-1,SPEED_PARITY,true,NONE,PLAUSIBLE,NONE,NONE,12,24,4,30", row);
    }

    @Test
    void anEnemySpeedRowHasOneCellPerHeaderColumn() {
        String row = SpeedParityLogger.enemySpeedRow("game", 6000, UpgradeType.Metabolic_Boost, Evidence.SEEN);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6000,ENEMY_SPEED,NONE,NONE,NONE,NONE,NONE,Metabolic_Boost,SEEN,-1,-1,-1,-1", row);
    }

    @Test
    void anOwnSpeedRowHasOneCellPerHeaderColumn() {
        String row = SpeedParityLogger.ownSpeedRow("game", 6011);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6011,OWN_SPEED,NONE,NONE,NONE,NONE,NONE,Metabolic_Boost,FINISHED,-1,-1,-1,-1", row);
    }

    @Test
    void aGateEndRowNamesTheTermThatEnded() {
        SpeedParityGate gate = new SpeedParityGate("squad-1", "NONE", false, "METABOLIC", "SEEN", 0, 0, 0, 0);

        String row = SpeedParityLogger.gateEndRow("game", 6011, "squad-1", "SPEED_PARITY", gate);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6011,GATE_END,squad-1,SPEED_PARITY,false,METABOLIC,SEEN,NONE,NONE,-1,-1,-1,-1", row);
    }
}
