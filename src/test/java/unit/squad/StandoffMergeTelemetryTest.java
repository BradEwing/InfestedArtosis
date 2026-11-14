package unit.squad;

import bwapi.Position;
import bwapi.TestUnits;
import bwapi.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.StagingRow;
import telemetry.StagingSink;
import telemetry.StagingTelemetry;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import unit.managed.Zergling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandoffMergeTelemetryTest {

    private static final int NOW = 4000;

    private final TestUnits units = new TestUnits();
    private final List<StagingRow> enters = new ArrayList<>();
    private final List<String> enterReasons = new ArrayList<>();

    private final StagingSink sink = new StagingSink() {
        @Override
        public void onStandoffEnter(int frame, String reason, StagingRow row) {
            enters.add(row);
            enterReasons.add(reason);
        }

        @Override
        public void onStandoffExit(int frame, String reason, StagingRow row) {
        }

        @Override
        public void onReinforcement(int frame, StagingRow row) {
        }

        @Override
        public void onReinforcementOutcome(int frame, String outcome, StagingRow row) {
        }

        @Override
        public void onLaunch(int frame, String reason, StagingRow row) {
        }

        @Override
        public void onLaunchHeld(int frame, String reason, StagingRow row) {
        }
    };

    @AfterEach
    void clearSink() {
        StagingTelemetry.clear();
    }

    private ManagedUnit ling(int id) {
        return new Zergling(units.game(), units.unit(UnitType.Zerg_Zergling, id), UnitRole.FIGHT, null);
    }

    private Squad squadOf(ManagedUnit... members) {
        Squad squad = new GroundSquad();
        for (ManagedUnit member : members) {
            squad.addUnit(member);
        }
        return squad;
    }

    @Test
    void theMergedEnterRowCountsTheMembersThatMovedInAndMarksTheNewcomersAsJoiners() {
        StagingTelemetry.register(sink);
        ManagedUnit staged1 = ling(1);
        ManagedUnit staged2 = ling(2);
        ManagedUnit newcomer = ling(3);
        Squad staging = squadOf(staged1, staged2);
        staging.getStandoff().begin(NOW - 50, new Position(500, 500), 11);
        staged1.joinStandoffEpisode(11, false);
        staged2.joinStandoffEpisode(11, false);
        Squad other = squadOf(newcomer);
        Squad merged = squadOf(staged1, staged2, newcomer);

        SquadManager.foldStandoff(merged, new HashSet<>(Arrays.asList(staging, other)), NOW);

        assertEquals(1, enters.size());
        StagingRow enter = enters.get(0);
        assertEquals(merged.getSupply(), enter.getSupply());
        assertTrue(enter.getSupply() > 0);
        assertEquals(3, enter.getMembers());
        assertEquals(11, enter.getEpisodeId());
        assertEquals("MERGED", enterReasons.get(0));
        assertEquals(11, newcomer.getStandoffEpisodeId());
        assertTrue(newcomer.isStandoffJoiner());
        assertFalse(staged1.isStandoffJoiner());
    }
}
