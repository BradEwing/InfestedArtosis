package telemetry;

import bwapi.UnitType;
import bwapi.UpgradeType;
import info.tracking.EnemySpeedTracker.Anchor;
import info.tracking.EnemySpeedTracker.Event;
import info.tracking.EnemySpeedTracker.Report;
import info.tracking.SpeedRunDetector;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpeedParityLoggerTest {

    private static final String NO_DIAGNOSTIC = "-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1";

    private static int columns(String csv) {
        return csv.split(",", -1).length;
    }

    @Test
    void aGateRowCarriesTheEnemyEvidenceAndBoundWithOneCellPerHeaderColumn() {
        SpeedParityGate gate = new SpeedParityGate("squad-1", "SPEED_PARITY", true, "NONE", "SEEN", 2700, "FLOOR", 12,
                24, 4, 30);

        String row = SpeedParityLogger.gateRow("game", 4000, gate);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,4000,GATE,squad-1,SPEED_PARITY,true,NONE,SEEN,Metabolic_Boost,SEEN,12,24,4,30,-1,-1,-1,"
                + "2700,FLOOR,NONE," + NO_DIAGNOSTIC, row);
    }

    @Test
    void aSeenRowCarriesTheRunAndTheBound() {
        Report report = new Report(Event.SEEN, UpgradeType.Metabolic_Boost, 7.25, 5.49, 16, 4300,
                Anchor.GAS_READING);

        String row = SpeedParityLogger.enemySpeedRow("game", 6000, report);

        assertEquals(40, columns(SpeedParityLogger.HEADER));
        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6000,ENEMY_SPEED,NONE,NONE,NONE,NONE,NONE,Metabolic_Boost,SEEN,-1,-1,-1,-1,"
                + "7.250,5.490,16,4300,GAS_READING,ENEMY," + NO_DIAGNOSTIC, row);
    }

    @Test
    void aPlausibleRowCarriesTheBoundAndNoWindow() {
        Report report = new Report(Event.PLAUSIBLE, UpgradeType.Muscular_Augments, -1, -1, -1, 5284,
                Anchor.EXTRACTOR);

        String row = SpeedParityLogger.enemySpeedRow("game", 5284, report);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,5284,ENEMY_SPEED,NONE,NONE,NONE,NONE,NONE,Muscular_Augments,PLAUSIBLE,-1,-1,-1,-1,"
                + "-1,-1,-1,5284,EXTRACTOR,ENEMY," + NO_DIAGNOSTIC, row);
    }

    @Test
    void aSelfSpeedRowHasNoAnchorAndTheSelfSide() {
        Report report = new Report(Event.EARLY_RUN, UpgradeType.Metabolic_Boost, 6.5, 5.49, 16, -1, null);

        String row = SpeedParityLogger.selfSpeedRow("game", 3000, report);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,3000,SELF_SPEED,NONE,NONE,NONE,NONE,NONE,Metabolic_Boost,EARLY_RUN,-1,-1,-1,-1,"
                + "6.500,5.490,16,-1,NONE,SELF," + NO_DIAGNOSTIC, row);
    }

    @Test
    void aDiagnosticRowCarriesTheTallyTheClearsAndTheStepHistogram() {
        SpeedRunDetector.Diagnostic diagnostic = new SpeedRunDetector.Diagnostic(UnitType.Zerg_Hydralisk, 4000, 3,
                0.9, 1.2, 1.1, 250, 12, 40, 7, 2, new int[] {5, 100, 20, 10, 3, 2}, 1.05, 1.31, 9, 4);

        String row = SpeedParityLogger.diagnosticRow("game", 9000, SpeedParitySink.Side.ENEMY, diagnostic);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,9000,SPEED_DIAG,NONE,NONE,NONE,NONE,NONE,Muscular_Augments,NONE,-1,-1,-1,-1,-1,"
                + String.format(Locale.ROOT, "%.3f", UnitType.Zerg_Hydralisk.topSpeed()) + ","
                + SpeedRunDetector.WINDOW_FRAMES + ",-1,NONE,ENEMY,3,0.900,1.200,1.100,250,12,40,4000,7,2,"
                + "5,100,20,10,3,2,1.050,1.310,9,4", row);
    }

    @Test
    void anOwnSpeedRowHasOneCellPerHeaderColumn() {
        String row = SpeedParityLogger.ownSpeedRow("game", 6011);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6011,OWN_SPEED,NONE,NONE,NONE,NONE,NONE,Metabolic_Boost,FINISHED,-1,-1,-1,-1,"
                + "-1,-1,-1,-1,NONE,SELF," + NO_DIAGNOSTIC, row);
    }

    @Test
    void aGateEndRowNamesTheTermThatEnded() {
        SpeedParityGate gate = new SpeedParityGate("squad-1", "NONE", false, "METABOLIC", "SEEN", 2700, "FLOOR", 0, 0,
                0, 0);

        String row = SpeedParityLogger.gateEndRow("game", 6011, "squad-1", "SPEED_PARITY", gate);

        assertEquals(columns(SpeedParityLogger.HEADER), columns(row));
        assertEquals("game,6011,GATE_END,squad-1,SPEED_PARITY,false,METABOLIC,SEEN,Metabolic_Boost,SEEN,-1,-1,-1,-1,"
                + "-1,-1,-1,2700,FLOOR,NONE," + NO_DIAGNOSTIC, row);
    }
}
