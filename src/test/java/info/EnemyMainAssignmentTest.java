package info;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enemy main assignment on game LV28400N's (4)Icarus starts. A building's BWEM Area needs a live map, so each
 * test says which starting location's Area the building stands in with a predicate over the starts.
 */
class EnemyMainAssignmentTest {

    private static final Predicate<Base> IN_NO_START_AREA = start -> false;

    private LV28400NFixture icarus;

    private BaseData baseData;

    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        icarus = new LV28400NFixture();
        baseData = icarus.baseData;
        PlanEvents.register(new EnemyMainEventRecorder(events));
    }

    @AfterEach
    void tearDown() {
        PlanEvents.clear();
    }

    /**
     * The proxy Gateway first shown at frame 3722 stands nearer the empty start than ours, so the nearest-start
     * rule took the empty start for the enemy main.
     */
    @Test
    void aProxyGatewayOutsideEveryStartAreaDoesNotSetTheEnemyMain() {
        Position gateway = LV28400NFixture.PROXY_GATEWAY;
        assertTrue(gateway.getDistance(icarus.emptyStart.getCenter()) < gateway.getDistance(icarus.ourMain.getCenter()));

        assertFalse(offer(UnitType.Protoss_Gateway, gateway, IN_NO_START_AREA));

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getEnemyNaturalBase());
        assertFalse(baseData.isEnemyMainBaseFound());
        assertFalse(baseData.getEnemyBases().contains(icarus.emptyStart));
        assertTrue(events.isEmpty());
    }

    @Test
    void aDepotOnAStartingLocationSetsTheEnemyMainAndItsNatural() {
        assertTrue(offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.DEPOT, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertTrue(baseData.getEnemyBases().contains(icarus.realMain));
        assertEquals(Collections.singletonList("ENEMY_MAIN_ASSIGNED [116, 47] DEPOT Protoss_Nexus [3776, 1552]"),
                events);
    }

    @Test
    void aBuildingInAStartingLocationsAreaSetsTheEnemyMain() {
        Position pylon = new Position(3584, 1424);

        assertTrue(offer(UnitType.Protoss_Pylon, pylon, start -> start == icarus.realMain));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.MAIN_AREA, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertEquals(Collections.singletonList("ENEMY_MAIN_ASSIGNED [116, 47] MAIN_AREA Protoss_Pylon [3584, 1424]"),
                events);
    }

    @Test
    void aDepotAtANaturalIsNoEvidenceOfTheMain() {
        Position naturalNexus = new Position(104 * 32 + 64, 47 * 32 + 48);

        assertFalse(offer(UnitType.Protoss_Nexus, naturalNexus, IN_NO_START_AREA));

        assertNull(baseData.getMainEnemyBase());
    }

    @Test
    void aBuildingInOurMainsAreaIsNoEvidenceOfTheEnemyMain() {
        assertFalse(offer(UnitType.Protoss_Pylon, new Position(1600, 400), start -> start == icarus.ourMain));

        assertNull(baseData.getMainEnemyBase());
    }

    @Test
    void aMainFromABuildingInItsAreaIsReplacedByADepotAtAnotherStartAndTheNaturalFollows() {
        offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, start -> start == icarus.emptyStart);
        assertSame(icarus.emptyStart, baseData.getMainEnemyBase());
        assertSame(icarus.emptyStartNatural, baseData.getEnemyNaturalBase());

        assertTrue(offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.DEPOT, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertFalse(baseData.getEnemyBases().contains(icarus.emptyStart));
        assertEquals(Arrays.asList(
                "ENEMY_MAIN_ASSIGNED [8, 77] MAIN_AREA Protoss_Pylon [384, 1696]",
                "ENEMY_MAIN_CLEARED [8, 77] REPLACED_BY_DEPOT",
                "ENEMY_MAIN_ASSIGNED [116, 47] DEPOT Protoss_Nexus [3776, 1552]"), events);
    }

    @Test
    void aMainFromADepotIsNotReplacedByABuildingInAnotherStartsArea() {
        offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA);

        assertFalse(offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, start -> start == icarus.emptyStart));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
    }

    @Test
    void aMainFromADepotIsNotReplacedByADepotAtAnotherStart() {
        offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA);

        assertFalse(offer(UnitType.Protoss_Nexus, new Position(8 * 32 + 64, 77 * 32 + 48), IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
    }

    @Test
    void aMainFromABuildingInItsAreaGainsDepotEvidenceWhenItsDepotIsSeen() {
        offer(UnitType.Protoss_Pylon, new Position(3584, 1424), start -> start == icarus.realMain);

        assertFalse(offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA));

        assertEquals(EnemyMainEvidence.DEPOT, baseData.getMainEnemyBaseEvidence());
        assertEquals(1, events.size());
    }

    /**
     * InformationManager.checkEnemyBases marks the start seen empty before it drops the main, as here.
     */
    @Test
    void anEmptyStartClearedWhileItsProxyIsStillVisibleIsNotAssignedAgain() {
        Predicate<Base> inEmptyStartArea = start -> start == icarus.emptyStart;
        offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, inEmptyStartArea);

        baseData.markStartSeenEmpty(icarus.emptyStart);
        baseData.removeEnemyBase(icarus.emptyStart, EnemyMainClearReason.NO_BUILDING_SEEN);

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getEnemyNaturalBase());
        assertFalse(offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, inEmptyStartArea));
        assertFalse(offer(UnitType.Protoss_Gateway, LV28400NFixture.SECOND_PROXY_GATEWAY, inEmptyStartArea));
        assertNull(baseData.getMainEnemyBase());
        assertEquals(Arrays.asList(
                "ENEMY_MAIN_ASSIGNED [8, 77] MAIN_AREA Protoss_Pylon [384, 1696]",
                "ENEMY_MAIN_CLEARED [8, 77] NO_BUILDING_SEEN"), events);
    }

    @Test
    void aDepotLaterBuiltOnAStartSeenEmptyStillSetsTheEnemyMain() {
        baseData.markStartSeenEmpty(icarus.emptyStart);

        assertTrue(offer(UnitType.Protoss_Nexus, new Position(8 * 32 + 64, 77 * 32 + 48), IN_NO_START_AREA));

        assertSame(icarus.emptyStart, baseData.getMainEnemyBase());
        assertFalse(baseData.isStartSeenEmpty(icarus.emptyStart));
    }

    @Test
    void ourOwnMainIsNeverMarkedSeenEmpty() {
        baseData.markStartSeenEmpty(icarus.ourMain);
        baseData.markStartSeenEmpty(icarus.realNatural);

        assertFalse(baseData.isStartSeenEmpty(icarus.ourMain));
        assertFalse(baseData.isStartSeenEmpty(icarus.realNatural));
    }

    @Test
    void destroyingTheMainsDepotClearsTheMainWithItsReason() {
        offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA);

        baseData.removeEnemyBase(icarus.realMain, EnemyMainClearReason.DEPOT_DESTROYED);

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getMainEnemyBaseEvidence());
        assertEquals("ENEMY_MAIN_CLEARED [116, 47] DEPOT_DESTROYED", events.get(events.size() - 1));
    }

    private boolean offer(UnitType type, Position position, Predicate<Base> standsInArea) {
        return baseData.offerEnemyMainEvidence(type, depotTile(type, position), position, standsInArea);
    }

    /**
     * A building's tile is its top-left tile, which for a 4x3 depot is its centre less (64,48).
     */
    private static TilePosition depotTile(UnitType type, Position centre) {
        return new Position(centre.getX() - type.tileWidth() * 16, centre.getY() - type.tileHeight() * 16)
                .toTilePosition();
    }
}
