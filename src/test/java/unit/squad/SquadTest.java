package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquadTest {

    private static Squad squad(SquadStatus status) {
        Squad squad = new Squad();
        squad.setStatus(status);
        return squad;
    }

    private static Squad containingSquad(int containStartFrame) {
        Squad squad = squad(SquadStatus.CONTAIN);
        squad.setContainStartFrame(containStartFrame);
        return squad;
    }

    private static Squad merge(Squad... sources) {
        Squad merged = new Squad();
        merged.setStatus(SquadStatus.FIGHT);
        merged.inheritStateFrom(Arrays.asList(sources));
        return merged;
    }

    @Test
    void mergedStatusIsIndependentOfIterationOrder() {
        for (SquadStatus first : SquadStatus.values()) {
            for (SquadStatus second : SquadStatus.values()) {
                assertEquals(
                        merge(squad(first), squad(second)).getStatus(),
                        merge(squad(second), squad(first)).getStatus(),
                        first + " merged with " + second + " must not depend on order");
            }
        }
    }

    @Test
    void fightOutranksEveryOtherStatus() {
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.FIGHT), squad(SquadStatus.RETREAT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.FIGHT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.CONTAIN), squad(SquadStatus.FIGHT)).getStatus());
        assertEquals(SquadStatus.FIGHT, merge(squad(SquadStatus.RALLY), squad(SquadStatus.FIGHT)).getStatus());
    }

    @Test
    void containOutranksRetreatAndRally() {
        assertEquals(SquadStatus.CONTAIN, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.CONTAIN)).getStatus());
        assertEquals(SquadStatus.CONTAIN, merge(squad(SquadStatus.RALLY), squad(SquadStatus.CONTAIN)).getStatus());
    }

    @Test
    void retreatOutranksRally() {
        assertEquals(SquadStatus.RETREAT, merge(squad(SquadStatus.RALLY), squad(SquadStatus.RETREAT)).getStatus());
    }

    @Test
    void mergedStatusIgnoresTheStatusOfTheSquadBeingMergedInto() {
        assertEquals(SquadStatus.RETREAT, merge(squad(SquadStatus.RETREAT), squad(SquadStatus.RALLY)).getStatus());
    }

    @Test
    void containStartFrameCarriesEarliestStartWhenMergedStatusIsContain() {
        Squad merged = merge(containingSquad(600), containingSquad(200));
        Squad reversed = merge(containingSquad(200), containingSquad(600));

        assertEquals(SquadStatus.CONTAIN, merged.getStatus());
        assertEquals(200, merged.getContainStartFrame());
        assertEquals(merged.getContainStartFrame(), reversed.getContainStartFrame());
    }

    @Test
    void containStartFrameIsClearedWhenMergedStatusIsNotContain() {
        Squad merged = merge(containingSquad(200), squad(SquadStatus.FIGHT));

        assertEquals(SquadStatus.FIGHT, merged.getStatus());
        assertEquals(0, merged.getContainStartFrame());
    }

    @Test
    void hysteresisLocksTakeTheLaterDeadline() {
        Squad early = squad(SquadStatus.FIGHT);
        early.setFightLockedUntilFrame(100);
        early.setRetreatLockedUntilFrame(400);
        early.setContainLockedUntilFrame(150);

        Squad late = squad(SquadStatus.FIGHT);
        late.setFightLockedUntilFrame(300);
        late.setRetreatLockedUntilFrame(200);
        late.setContainLockedUntilFrame(500);

        Squad merged = merge(early, late);
        Squad reversed = merge(late, early);

        assertEquals(300, merged.getFightLockedUntilFrame());
        assertEquals(400, merged.getRetreatLockedUntilFrame());
        assertEquals(500, merged.getContainLockedUntilFrame());
        assertEquals(merged.getFightLockedUntilFrame(), reversed.getFightLockedUntilFrame());
        assertEquals(merged.getRetreatLockedUntilFrame(), reversed.getRetreatLockedUntilFrame());
        assertEquals(merged.getContainLockedUntilFrame(), reversed.getContainLockedUntilFrame());
    }
}
