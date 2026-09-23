package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadRunbyMembershipTest {

    private static Squad squad(SquadStatus status) {
        Squad squad = new Squad();
        squad.setStatus(status);
        return squad;
    }

    @Test
    void runbyOutranksFight() {
        assertEquals(SquadStatus.RUNBY, SquadStatus.dominant(SquadStatus.RUNBY, SquadStatus.FIGHT));
        assertEquals(SquadStatus.RUNBY, SquadStatus.dominant(SquadStatus.FIGHT, SquadStatus.RUNBY));
    }

    @Test
    void runbyOutranksEveryOtherStatus() {
        for (SquadStatus other : SquadStatus.values()) {
            assertEquals(SquadStatus.RUNBY, SquadStatus.dominant(SquadStatus.RUNBY, other));
        }
    }

    @Test
    void aRunbySquadIsNeverMerged() {
        assertFalse(SquadManager.mayMerge(SquadStatus.RUNBY));
        assertTrue(SquadManager.mayMerge(SquadStatus.FIGHT));
        assertTrue(SquadManager.mayMerge(SquadStatus.CONTAIN));
    }

    @Test
    void aRunbySquadIsNeverSplit() {
        assertFalse(SquadManager.maySplit(SquadStatus.RUNBY));
        assertFalse(SquadManager.maySplit(SquadStatus.CONTAIN));
        assertFalse(SquadManager.maySplit(SquadStatus.RALLY));
        assertFalse(SquadManager.maySplit(SquadStatus.RETREAT));
        assertTrue(SquadManager.maySplit(SquadStatus.FIGHT));
    }

    @Test
    void aRunbySquadTakesNoReinforcements() {
        assertFalse(SquadManager.mayJoin(SquadStatus.RUNBY));
        assertTrue(SquadManager.mayJoin(SquadStatus.FIGHT));
        assertTrue(SquadManager.mayJoin(SquadStatus.RALLY));
    }

    @Test
    void aMergeThatEndsInRunbyKeepsTheRunbyState() {
        Squad runby = squad(SquadStatus.RUNBY);
        RunbyState state = new RunbyState(7000);
        runby.setRunbyState(state);

        Squad merged = new Squad();
        merged.inheritStateFrom(Arrays.asList(squad(SquadStatus.FIGHT), runby));

        assertEquals(SquadStatus.RUNBY, merged.getStatus());
        assertSame(state, merged.getRunbyState());
    }

    @Test
    void aSplitOffAnythingElseCarriesNoRunbyState() {
        Squad fight = squad(SquadStatus.FIGHT);
        fight.setRunbyState(new RunbyState(7000));

        Squad child = new Squad();
        child.inheritStateFrom(fight);

        assertNull(child.getRunbyState());
    }
}
