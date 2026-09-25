package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;
import util.TargetScorer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaturatedTargetWideningTest {

    private static final class Enemy {
        private final int id;
        private final UnitType type;
        private final int distance;
        private final boolean admitted;

        Enemy(int id, UnitType type, int distance, boolean admitted) {
            this.id = id;
            this.type = type;
            this.distance = distance;
            this.admitted = admitted;
        }
    }

    private static TargetScorer.Selection selection(boolean saturated) {
        return new TargetScorer.Selection(null, TargetScorer.Priority.CRITICAL, 1, TargetScorer.Reason.THREAT,
                saturated ? 8 : 0, saturated, "squad-1", false);
    }

    private static List<Enemy> widen(List<Enemy> enemies) {
        return SquadManager.widenCandidates(enemies, e -> e.distance, e -> e.admitted);
    }

    @Test
    void wideningKeepsTheNearbyCandidatesAndAddsAdmittedOnesOutToTwiceTheTargetingRadius() {
        Enemy near = new Enemy(1, UnitType.Terran_Marine, 100, false);
        Enemy edge = new Enemy(2, UnitType.Terran_Marine, 256, false);
        Enemy wide = new Enemy(3, UnitType.Terran_Marine, 512, true);
        Enemy wideButRefused = new Enemy(4, UnitType.Terran_Marine, 400, false);
        Enemy tooFar = new Enemy(5, UnitType.Terran_Marine, 513, true);

        assertEquals(Arrays.asList(near, edge, wide), widen(Arrays.asList(near, edge, wide, wideButRefused, tooFar)));
    }

    @Test
    void anUnsaturatedPickStandsWithoutBuildingTheWidenedCandidates() {
        TargetScorer.Selection open = selection(false);

        TargetScorer.Selection kept = SquadManager.<Enemy>widenWhenSaturated(open, 1, () -> {
            throw new AssertionError("widened candidates built");
        }, candidates -> {
            throw new AssertionError("selected again");
        });

        assertSame(open, kept);
        assertNull(SquadManager.<Enemy>widenWhenSaturated(null, 0, Collections::emptyList, c -> selection(false)));
    }

    @Test
    void aSaturatedPickIsReplacedByAnOpenOneFromTheWidenedCandidates() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Arrays.asList(new Enemy(1, UnitType.Terran_Marine, 30, true),
                new Enemy(2, UnitType.Terran_Marine, 400, true));

        TargetScorer.Selection kept = SquadManager.widenWhenSaturated(full, 1, () -> widened,
                candidates -> selection(false));

        assertFalse(kept.isSaturated());
        assertTrue(kept.isWidened());
        assertFalse(full.isWidened());
    }

    @Test
    void aSaturatedPickStandsWhenTheWidenedPickIsSaturatedToo() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Arrays.asList(new Enemy(1, UnitType.Terran_Marine, 30, true),
                new Enemy(2, UnitType.Terran_Marine, 400, true));

        assertSame(full, SquadManager.widenWhenSaturated(full, 1, () -> widened, candidates -> selection(true)));
    }

    @Test
    void aSaturatedPickStandsWhenWideningAddsNoCandidate() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Collections.singletonList(new Enemy(1, UnitType.Terran_Marine, 30, true));

        assertSame(full, SquadManager.widenWhenSaturated(full, 1, () -> widened, candidates -> {
            throw new AssertionError("selected again");
        }));
    }

    @Test
    void onlyAFightingMemberWithALiveTargetIsSeededIntoTheFrameLedger() {
        assertTrue(SquadManager.seedsFightTarget(UnitRole.FIGHT, true));
        assertFalse(SquadManager.seedsFightTarget(UnitRole.FIGHT, false));
        for (UnitRole role : Arrays.asList(UnitRole.RETREAT, UnitRole.RALLY, UnitRole.CONTAIN, UnitRole.RUNBY)) {
            assertFalse(SquadManager.seedsFightTarget(role, true), role.toString());
        }
    }
}
