package unit.squad;

import org.junit.jupiter.api.Test;
import telemetry.DecisionPath;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentReentryCooldown.COOLDOWN_FRAMES;

class ContainmentReentryCooldownTest {

    private static final int EXIT_FRAME = 7787;
    private static final int SQUAD_SUPPLY = 10;
    private static final int ENEMY_SUPPLY = 40;

    private static ContainmentReentryCooldown armed() {
        ContainmentReentryCooldown cooldown = new ContainmentReentryCooldown();
        cooldown.arm(EXIT_FRAME, SQUAD_SUPPLY, ENEMY_SUPPLY);
        return cooldown;
    }

    @Test
    void aSquadThatNeverExitedByAttritionIsNotBarred() {
        assertFalse(new ContainmentReentryCooldown().blocks(EXIT_FRAME, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }

    @Test
    void theSameSquadCannotReenterWithinTheCooldownAfterAnAttritionExit() {
        ContainmentReentryCooldown cooldown = armed();
        assertTrue(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY));
        assertTrue(cooldown.blocks(EXIT_FRAME + COOLDOWN_FRAMES - 1, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }

    @Test
    void theSquadMayReenterOnceTheCooldownRunsOut() {
        assertFalse(armed().blocks(EXIT_FRAME + COOLDOWN_FRAMES, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }

    @Test
    void aMaterialGrowthOfTheSquadEndsTheCooldown() {
        ContainmentReentryCooldown cooldown = armed();
        assertTrue(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY + 4, ENEMY_SUPPLY));
        assertFalse(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY + 5, ENEMY_SUPPLY));
    }

    @Test
    void aMaterialShrinkOfTheEnemyArmyEndsTheCooldown() {
        ContainmentReentryCooldown cooldown = armed();
        assertTrue(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY - 10));
        assertFalse(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY - 14));
    }

    @Test
    void anEnemyThatGrewDoesNotEndTheCooldown() {
        assertTrue(armed().blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY * 2));
    }

    @Test
    void aMergeKeepsTheLongestCooldown() {
        ContainmentReentryCooldown merged = new ContainmentReentryCooldown();
        ContainmentReentryCooldown early = new ContainmentReentryCooldown();
        early.arm(EXIT_FRAME - 1000, SQUAD_SUPPLY, ENEMY_SUPPLY);
        merged.absorb(early);
        merged.absorb(armed());
        merged.absorb(early);
        assertTrue(merged.blocks(EXIT_FRAME + 1000, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }

    @Test
    void aSquadInsideItsCooldownMayNotTakeAnArcWhateverElseAllows() {
        ContainmentEscalation escalation = new ContainmentEscalation();
        ContainmentStalemate stalemate = new ContainmentStalemate();
        assertTrue(SquadManager.mayTakeArc(escalation, stalemate, EXIT_FRAME, false, true, false, false));
        assertFalse(SquadManager.mayTakeArc(escalation, stalemate, EXIT_FRAME, false, true, false, true));
    }

    @Test
    void onlyAnAttritionOrOutrangedExitAgainstTerranArmsTheCooldown() {
        assertTrue(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_ATTRITION, true));
        assertTrue(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_OUTRANGED, true));
        assertFalse(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_RETREAT, true));
        assertFalse(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_STALEMATE, true));
        assertFalse(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_ATTRITION, false));
        assertFalse(SquadManager.armsReentryCooldown(DecisionPath.CONTAIN_OUTRANGED, false));
    }

    @Test
    void aMergeOrSplitCarriesTheCooldownToTheNewSquad() {
        Squad source = new Squad();
        source.setStatus(SquadStatus.RETREAT);
        source.getContainmentReentryCooldown().arm(EXIT_FRAME, SQUAD_SUPPLY, ENEMY_SUPPLY);
        Squad other = new Squad();
        other.setStatus(SquadStatus.RETREAT);
        Squad merged = new Squad();
        merged.inheritStateFrom(Arrays.asList(other, source));
        assertTrue(merged.getContainmentReentryCooldown().blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY));
        Squad child = new Squad();
        child.inheritStateFrom(source);
        assertTrue(child.getContainmentReentryCooldown().blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }

    @Test
    void resetClearsTheCooldown() {
        ContainmentReentryCooldown cooldown = armed();
        cooldown.reset();
        assertFalse(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }
}
