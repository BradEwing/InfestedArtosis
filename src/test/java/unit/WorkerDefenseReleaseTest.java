package unit;

import macro.plan.PlanState;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerDefenseReleaseTest {

    @Test
    void aBuilderDispatchedOutOfTheSquadIsNotSentBackToMining() {
        assertFalse(UnitManager.returnsToMiningOnRelease(UnitRole.BUILD, PlanState.BUILDING));
    }

    @Test
    void aReleasedDefenderReturnsToMining() {
        assertTrue(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, null));
    }

    @Test
    void aDefenderWithAFinishedPlanReturnsToMining() {
        assertTrue(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, PlanState.COMPLETE));
        assertTrue(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, PlanState.CANCELLED));
    }

    @Test
    void aDefenderWithAScheduledPlanReturnsToMiningUntilItsDispatch() {
        assertTrue(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, PlanState.SCHEDULE));
    }

    @Test
    void aDefenderWhosePlanIsUnderWayKeepsIt() {
        assertFalse(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, PlanState.BUILDING));
        assertFalse(UnitManager.returnsToMiningOnRelease(UnitRole.DEFEND, PlanState.MORPHING));
    }

    @Test
    void aSquadMemberThatPickedUpAnotherRoleKeepsIt() {
        assertFalse(UnitManager.returnsToMiningOnRelease(UnitRole.SCOUT, null));
        assertFalse(UnitManager.returnsToMiningOnRelease(UnitRole.BUILD, null));
    }

    @Test
    void aGathererWithNoPlanMayBePulled() {
        assertTrue(UnitManager.mayPullToDefend(UnitRole.GATHER, null));
        assertTrue(UnitManager.mayPullToDefend(UnitRole.GATHER, PlanState.COMPLETE));
        assertTrue(UnitManager.mayPullToDefend(UnitRole.GATHER, PlanState.CANCELLED));
    }

    @Test
    void aDispatchedBuilderIsNeverADefenceCandidate() {
        assertFalse(UnitManager.mayPullToDefend(UnitRole.BUILD, PlanState.BUILDING));
        assertFalse(UnitManager.mayPullToDefend(UnitRole.BUILD, null));
    }

    @Test
    void aDroneScheduledToBuildIsNotADefenceCandidate() {
        assertFalse(UnitManager.mayPullToDefend(UnitRole.GATHER, PlanState.SCHEDULE));
        assertFalse(UnitManager.mayPullToDefend(UnitRole.GATHER, PlanState.PLANNED));
    }
}
