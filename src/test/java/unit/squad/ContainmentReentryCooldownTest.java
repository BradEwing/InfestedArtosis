package unit.squad;

import org.junit.jupiter.api.Test;

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
    void resetClearsTheCooldown() {
        ContainmentReentryCooldown cooldown = armed();
        cooldown.reset();
        assertFalse(cooldown.blocks(EXIT_FRAME + 192, SQUAD_SUPPLY, ENEMY_SUPPLY));
    }
}
