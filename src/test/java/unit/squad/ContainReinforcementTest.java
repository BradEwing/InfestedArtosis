package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WalkPosition;
import org.junit.jupiter.api.Test;
import util.Arc;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.SquadManager.arcToJoin;
import static unit.squad.SquadManager.containmentDefensePadding;
import static unit.squad.SquadManager.coveredByStaticDefense;
import static unit.squad.SquadManager.filterByProximity;
import static unit.squad.SquadManager.keepsJoiningContain;

class ContainReinforcementTest {

    private static final Position CHOKE = new Position(1584, 504);
    private static final Position ENEMY_BASE = new Position(2100, 450);
    private static final Position BUNKER = new Position(1776, 416);
    private static final Position BARRACKS = new Position(2080, 560);
    private static final Position MARINE_UNDER_BUNKER = new Position(1695, 561);
    private static final Position REINFORCEMENT_CENTER = new Position(1922, 977);
    private static final Position LEAD_LING = new Position(1630, 545);
    private static final int LEARNED_BUNKER_REACH = 167;
    private static final int ARC_RADIUS = 160;
    private static final int ARC_DEGREES = 90;
    private static final int ARC_POINTS = 22;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final int LING_PADDING = containmentDefensePadding(Collections.singleton(UnitType.Zerg_Zergling));
    private static final List<StaticDefenseZone> BUNKER_ZONES = Collections.singletonList(
            new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, LEARNED_BUNKER_REACH));

    private static Arc containArc(Position choke, Position enemyBase) {
        Position face = new Position(2 * choke.getX() - enemyBase.getX(), 2 * choke.getY() - enemyBase.getY());
        Arc arc = new Arc(choke, face, ARC_RADIUS, ARC_DEGREES, ARC_POINTS);
        arc.compute(ALL_WALKABLE, BUNKER_ZONES, LING_PADDING, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static final class Enemy {
        private final double distance;
        private final boolean underStaticDefense;

        private Enemy(double distance, boolean underStaticDefense) {
            this.distance = distance;
            this.underStaticDefense = underStaticDefense;
        }
    }

    @Test
    void aReinforcementWithAContainActiveMovesToTheArcNotTheEnemyBuilding() {
        Arc arc = containArc(CHOKE, ENEMY_BASE);
        assertFalse(arc.isEmpty());

        Arc joined = arcToJoin(REINFORCEMENT_CENTER, Collections.singletonList(arc));
        assertSame(arc, joined);

        Position target = joined.closestPosition(LEAD_LING);
        assertNotNull(target);
        assertTrue(arc.getPositions().contains(target));
        assertFalse(target.equals(BARRACKS));
        assertFalse(coveredByStaticDefense(target, BUNKER_ZONES, LING_PADDING),
                target + " is inside the bunker's learned reach");
        assertTrue(target.getDistance(BARRACKS) > CHOKE.getDistance(BARRACKS),
                target + " is not on our side of the choke");
    }

    @Test
    void withNoContainActiveThereIsNoArcToJoinAndTheMarchKeepsItsBuilding() {
        assertNull(arcToJoin(REINFORCEMENT_CENTER, Collections.emptyList()));
        assertNull(arcToJoin(REINFORCEMENT_CENTER, Collections.singletonList(null)));
    }

    @Test
    void aContainWithAnEmptyArcIsNotJoined() {
        Arc uncomputed = new Arc(CHOKE, REINFORCEMENT_CENTER, ARC_RADIUS, ARC_DEGREES, ARC_POINTS);
        assertTrue(uncomputed.isEmpty());

        assertNull(arcToJoin(REINFORCEMENT_CENTER, Collections.singletonList(uncomputed)));
        Arc held = containArc(CHOKE, ENEMY_BASE);
        assertSame(held, arcToJoin(REINFORCEMENT_CENTER, Arrays.asList(uncomputed, held)));
    }

    @Test
    void aReinforcementJoinsTheClosestOfTwoContains() {
        Arc near = containArc(CHOKE, ENEMY_BASE);
        Position farChoke = new Position(3500, 3500);
        Arc far = containArc(farChoke, new Position(3900, 3900));

        assertSame(near, arcToJoin(REINFORCEMENT_CENTER, Arrays.asList(far, near)));
        assertSame(far, arcToJoin(new Position(3300, 3300), Arrays.asList(near, far)));
    }

    @Test
    void aSquadWithNoCenterJoinsNothing() {
        assertNull(arcToJoin(null, Collections.singletonList(containArc(CHOKE, ENEMY_BASE))));
    }

    @Test
    void onlyACommittedRallyingSquadKeepsHeadingForTheContain() {
        assertTrue(keepsJoiningContain(SquadStatus.RALLY, true));
        assertFalse(keepsJoiningContain(SquadStatus.RALLY, false));
        assertFalse(keepsJoiningContain(SquadStatus.RETREAT, true));
        assertFalse(keepsJoiningContain(SquadStatus.FIGHT, true));
        assertFalse(keepsJoiningContain(SquadStatus.CONTAIN, true));
    }

    @Test
    void aMarineUnderTheBunkerIsCoveredAndTheArcIsNot() {
        assertTrue(coveredByStaticDefense(MARINE_UNDER_BUNKER, BUNKER_ZONES, LING_PADDING));
        assertTrue(coveredByStaticDefense(BUNKER, BUNKER_ZONES, LING_PADDING));
        for (Position point : containArc(CHOKE, ENEMY_BASE).getPositions()) {
            assertFalse(coveredByStaticDefense(point, BUNKER_ZONES, LING_PADDING));
        }
        assertFalse(coveredByStaticDefense(MARINE_UNDER_BUNKER, Collections.emptyList(), LING_PADDING));
    }

    @Test
    void theFallbackDropsTargetsUnderStaticDefense() {
        Enemy marineUnderBunker = new Enemy(300, true);
        Enemy marineInTheOpen = new Enemy(400, false);

        List<Enemy> targets = filterByProximity(Arrays.asList(marineUnderBunker, marineInTheOpen),
                e -> e.distance, e -> !e.underStaticDefense);

        assertEquals(Collections.singletonList(marineInTheOpen), targets);
    }

    @Test
    void theFallbackIsEmptyWhenEveryDistantTargetIsUnderStaticDefense() {
        Enemy marine = new Enemy(300, true);
        Enemy bunker = new Enemy(350, true);

        assertTrue(filterByProximity(Arrays.asList(marine, bunker), e -> e.distance,
                e -> !e.underStaticDefense).isEmpty());
    }

    @Test
    void nearbyTargetsAreKeptWhateverTheFallbackAdmits() {
        Enemy adjacent = new Enemy(256, true);
        Enemy distant = new Enemy(257, false);

        List<Enemy> targets = filterByProximity(Arrays.asList(adjacent, distant), e -> e.distance,
                e -> !e.underStaticDefense);

        assertEquals(Collections.singletonList(adjacent), targets);
    }

    @Test
    void withoutAFallbackRuleEveryDistantTargetIsKept() {
        Enemy marine = new Enemy(300, true);
        Enemy bunker = new Enemy(350, true);

        assertEquals(Arrays.asList(marine, bunker), filterByProximity(Arrays.asList(marine, bunker), e -> e.distance));
    }
}
