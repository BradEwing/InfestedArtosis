package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import bwapi.WalkPosition;
import info.GameState;
import info.tracking.EnemyReachMemory;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentArcTest {

    private static final Position CHOKE = new Position(1600, 1600);
    private static final Position ENEMY_MAIN = new Position(1600, 2240);
    private static final Position FACE_TARGET = new Position(1600, 960);
    private static final int ARC_RADIUS = 160;
    private static final int ARC_DEGREES = 90;
    private static final int NUM_POINTS = 8;
    private static final int MAP_PIXELS = 4096;
    private static final Set<WalkPosition> ALL_WALKABLE = Collections.emptySet();
    private static final int MARINE_RANGE = UnitType.Terran_Marine.groundWeapon().maxRange();
    private static final List<UnitType> LING_LURKER = Arrays.asList(UnitType.Zerg_Zergling, UnitType.Zerg_Lurker);
    private static final Position LSP4O05L_BUNKER = new Position(848, 896);
    private static final Position LSP4O05L_ARC_CENTER = new Position(824, 880);
    private static final Position LSP4O05L_FACE = new Position(824, 1520);
    private static final Position LSP4O05L_DEATH_POINT = new Position(831, 1102);
    private static final int LSP4O05L_POINTS = 9;
    private static final int LEARNED_BUNKER_REACH = 184;
    private static final List<UnitType> LINGS = Collections.singletonList(UnitType.Zerg_Zergling);

    private static StaticDefenseZone bunkerAt(Position position) {
        return new StaticDefenseZone(UnitType.Terran_Bunker, position, MARINE_RANGE);
    }

    private static Arc computeArc(List<StaticDefenseZone> zones, int padding) {
        Arc arc = new Arc(CHOKE, FACE_TARGET, ARC_RADIUS, ARC_DEGREES, NUM_POINTS);
        arc.compute(ALL_WALKABLE, zones, padding, MAP_PIXELS, MAP_PIXELS);
        return arc;
    }

    private static void assertNoPointWithinReachPlusPadding(Arc arc, List<StaticDefenseZone> zones, int padding) {
        assertFalse(arc.isEmpty());
        for (Position point : arc.getPositions()) {
            for (StaticDefenseZone zone : zones) {
                assertTrue(zone.edgeDistance(point.getX(), point.getY()) > zone.getReach() + padding,
                        point + " is within reach plus padding of the bunker at " + zone.getCenter());
            }
        }
    }

    @Test
    void noArcPointLiesWithinReachPlusPaddingOfABunkerAtTheChoke() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        List<StaticDefenseZone> zones = Collections.singletonList(bunkerAt(CHOKE));

        assertNoPointWithinReachPlusPadding(computeArc(zones, padding), zones, padding);
    }

    @Test
    void noArcPointLiesWithinReachPlusPaddingOfBunkersOnTheArcLine() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        List<StaticDefenseZone> zones = Arrays.asList(
                bunkerAt(new Position(CHOKE.getX(), CHOKE.getY() - ARC_RADIUS)),
                bunkerAt(new Position(CHOKE.getX() - 112, CHOKE.getY() - 112)),
                bunkerAt(new Position(CHOKE.getX() + 112, CHOKE.getY() - 112)));

        assertNoPointWithinReachPlusPadding(computeArc(zones, padding), zones, padding);
    }

    @Test
    void theArcFacesAwayFromTheEnemyMain() {
        Arc arc = computeArc(Collections.emptyList(), 0);

        for (Position point : arc.getPositions()) {
            assertTrue(point.getDistance(ENEMY_MAIN) > CHOKE.getDistance(ENEMY_MAIN));
        }
    }

    @Test
    void anArcWithNoPointClearOfCoverageIsEmpty() {
        int padding = SquadManager.containmentDefensePadding(LING_LURKER);
        StaticDefenseZone deepCoverage = new StaticDefenseZone(UnitType.Terran_Bunker, CHOKE, 1024);

        assertTrue(computeArc(Collections.singletonList(deepCoverage), padding).isEmpty());
    }

    @Test
    void paddingIsTheLargestMemberExtentPlusAMargin() {
        int lingExtent = maxDimension(UnitType.Zerg_Zergling);
        int lurkerExtent = maxDimension(UnitType.Zerg_Lurker);
        int margin = SquadManager.containmentDefensePadding(Collections.emptyList());

        assertTrue(margin > 0);
        assertEquals(lingExtent + margin,
                SquadManager.containmentDefensePadding(Collections.singletonList(UnitType.Zerg_Zergling)));
        assertEquals(Math.max(lingExtent, lurkerExtent) + margin,
                SquadManager.containmentDefensePadding(LING_LURKER));
    }

    private static List<StaticDefenseZone> lsp4o05lZones(EnemyReachMemory memory) {
        return GameState.staticDefenseZones(GameState.staticDefenseReaches(Race.Terran), memory,
                type -> type == UnitType.Terran_Bunker
                        ? Collections.singleton(LSP4O05L_BUNKER) : Collections.emptySet());
    }

    private static Arc lsp4o05lArc(int radius, List<StaticDefenseZone> zones, int padding) {
        return SquadManager.computeContainmentArc(
                new Arc(LSP4O05L_ARC_CENTER, LSP4O05L_FACE, radius, ARC_DEGREES, LSP4O05L_POINTS), zones, padding,
                ALL_WALKABLE, MAP_PIXELS, MAP_PIXELS);
    }

    private static EnemyReachMemory learnedBunkerReach() {
        EnemyReachMemory memory = new EnemyReachMemory();
        memory.raise(UnitType.Terran_Bunker, LEARNED_BUNKER_REACH, EnemyReachMemory.Source.BULLET,
                LSP4O05L_DEATH_POINT, 7546);
        return memory;
    }

    @Test
    void withLearnedBunkerReachNoArcPointLiesWithin227PixelsOfTheBunkerEdge() {
        int padding = SquadManager.containmentDefensePadding(LINGS);
        List<StaticDefenseZone> zones = lsp4o05lZones(learnedBunkerReach());
        StaticDefenseZone bunker = zones.get(0);

        assertEquals(43, padding);
        assertEquals(LEARNED_BUNKER_REACH, bunker.getReach());
        assertTrue(bunker.covers(LSP4O05L_DEATH_POINT, padding),
                "the point lings died on in LSP4O05L lies inside the learned reach plus padding");

        Arc arc = lsp4o05lArc(160, zones, padding);

        assertNotNull(arc);
        assertFalse(arc.getPositions().contains(LSP4O05L_DEATH_POINT));
        for (Position point : arc.getPositions()) {
            assertTrue(bunker.edgeDistance(point.getX(), point.getY()) > LEARNED_BUNKER_REACH + padding,
                    point + " lies within 227 px of the Bunker's edge");
        }
    }

    @Test
    void withoutALearnedReachTheArcStandsWhereTheLingsDied() {
        int padding = SquadManager.containmentDefensePadding(LINGS);
        List<StaticDefenseZone> zones = lsp4o05lZones(new EnemyReachMemory());
        StaticDefenseZone bunker = zones.get(0);

        Arc arc = lsp4o05lArc(160, zones, padding);

        assertNotNull(arc);
        boolean anyInsideLearnedReach = false;
        for (Position point : arc.getPositions()) {
            if (bunker.edgeDistance(point.getX(), point.getY()) <= LEARNED_BUNKER_REACH + padding) {
                anyInsideLearnedReach = true;
            }
        }
        assertTrue(anyInsideLearnedReach, "the Marine-range arc keeps points the lings were shot on");
    }

    @Test
    void aFreshSquadAfterAClearedEpisodeAndAMergeStillBuildsItsArcOutsideTheLearnedReach() {
        EnemyReachMemory memory = learnedBunkerReach();
        Squad episode = new Squad();
        episode.setStatus(SquadStatus.CONTAIN);
        episode.startContainLock(7450);
        episode.setContainRadius(288);
        episode.clearContainStart();
        episode.setStatus(SquadStatus.RETREAT);
        Squad reinforcement = new Squad();
        reinforcement.setStatus(SquadStatus.FIGHT);
        Squad merged = new Squad();
        merged.inheritStateFrom(Arrays.asList(episode, reinforcement));

        assertEquals(0, merged.getContainRadius(), "nothing learned in the episode survives on the squad");
        assertEquals(160, SquadManager.containmentRadius(merged));

        int padding = SquadManager.containmentDefensePadding(LINGS);
        List<StaticDefenseZone> zones = lsp4o05lZones(memory);
        Arc arc = lsp4o05lArc(SquadManager.containmentRadius(merged), zones, padding);

        assertNotNull(arc);
        for (Position point : arc.getPositions()) {
            assertTrue(zones.get(0).edgeDistance(point.getX(), point.getY()) > LEARNED_BUNKER_REACH + padding,
                    point + " is back inside the reach the game already learned");
        }
    }

    @Test
    void theShortestGroundRangeInASquadSetsWhatOutrangesIt() {
        assertEquals(UnitType.Zerg_Zergling.groundWeapon().maxRange(),
                SquadManager.shortestGroundRange(Arrays.asList(UnitType.Zerg_Hydralisk, UnitType.Zerg_Zergling,
                        UnitType.Zerg_Overlord)));
        assertEquals(0, SquadManager.shortestGroundRange(Collections.singletonList(UnitType.Zerg_Overlord)));
    }

    private static int maxDimension(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }
}
