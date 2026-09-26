package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SquadHarassMembershipTest {

    private static Squad squad(SquadStatus status) {
        Squad squad = new Squad();
        squad.setStatus(status);
        return squad;
    }

    @Test
    void harassOutranksFightAndYieldsOnlyToRunby() {
        assertEquals(SquadStatus.HARASS, SquadStatus.dominant(SquadStatus.HARASS, SquadStatus.FIGHT));
        assertEquals(SquadStatus.HARASS, SquadStatus.dominant(SquadStatus.RETREAT, SquadStatus.HARASS));
        assertEquals(SquadStatus.RUNBY, SquadStatus.dominant(SquadStatus.HARASS, SquadStatus.RUNBY));
    }

    @Test
    void aHarassSquadIsNeverMergedSplitOrJoined() {
        assertFalse(SquadManager.mayMerge(SquadStatus.HARASS));
        assertFalse(SquadManager.maySplit(SquadStatus.HARASS));
        assertFalse(SquadManager.mayJoin(SquadStatus.HARASS));
        assertFalse(SquadManager.mayJoinAirSquadAt(SquadStatus.HARASS, 0));
    }

    @Test
    void aMergeThatEndsInHarassKeepsTheHarassState() {
        Squad harass = squad(SquadStatus.HARASS);
        AirHarassState state = new AirHarassState(9000, 1080);
        harass.setHarassState(state);

        Squad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.asList(squad(SquadStatus.RETREAT), harass));

        assertEquals(SquadStatus.HARASS, merged.getStatus());
        assertSame(state, merged.getHarassState());
    }

    @Test
    void aSplitOffAnythingElseCarriesNoHarassStateButKeepsTheHarassExit() {
        Squad retreat = squad(SquadStatus.RETREAT);
        retreat.setHarassState(new AirHarassState(9000, 1080));
        retreat.setHarassExitFrame(9500);

        Squad child = new AirSquad();
        child.inheritStateFrom(retreat);

        assertNull(child.getHarassState());
        assertEquals(9500, child.getHarassExitFrame());
    }

    @Test
    void killsAreSortedIntoWorkersBuildingsAndEverythingElse() {
        assertEquals(AirHarassState.KillKind.WORKER, AirHarassController.killKind(UnitType.Terran_SCV));
        assertEquals(AirHarassState.KillKind.BUILDING, AirHarassController.killKind(UnitType.Terran_Supply_Depot));
        assertEquals(AirHarassState.KillKind.OTHER, AirHarassController.killKind(UnitType.Terran_Marine));
    }

    @Test
    void theStateCountsKillsAndLosses() {
        AirHarassState state = new AirHarassState(9000, 1080);

        state.creditKill(AirHarassState.KillKind.WORKER);
        state.creditKill(AirHarassState.KillKind.WORKER);
        state.creditKill(AirHarassState.KillKind.BUILDING);
        state.creditKill(AirHarassState.KillKind.OTHER);
        state.creditLoss();

        assertEquals(2, state.getWorkersKilled());
        assertEquals(1, state.getBuildingsKilled());
        assertEquals(1, state.getOtherKilled());
        assertEquals(1, state.getMutasLost());
    }

    @Test
    void aRetargetRestartsTransit() {
        AirHarassState state = new AirHarassState(9000, 1080);
        state.target(null, new Position(100, 100), 9000);
        state.arrive(9200);

        state.target(null, new Position(900, 900), 9800);

        assertEquals(AirHarassState.Phase.TRANSIT, state.getPhase());
        assertFalse(state.hasArrived());
        assertEquals(9800, state.getLastProgressFrame());
    }
}
