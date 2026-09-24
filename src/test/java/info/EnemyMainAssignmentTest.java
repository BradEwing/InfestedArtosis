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
 * test says which base's Area the building stands in with a predicate over the bases.
 */
class EnemyMainAssignmentTest {

    private static final Predicate<Base> IN_NO_START_AREA = base -> false;

    private static final Position FORGE_AT_REAL_NATURAL = new Position(3328, 1440);

    private static final Position CANNON_AT_REAL_NATURAL = new Position(3264, 1504);

    private static final Position SECOND_CANNON_AT_REAL_NATURAL = new Position(3264, 1568);

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
    void anEmptyStartClearedWhileItsProxyIsStillVisibleIsNotAssignedAgainButTheLastStartIs() {
        Predicate<Base> inEmptyStartArea = start -> start == icarus.emptyStart;
        offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, inEmptyStartArea);

        baseData.markStartSeenEmpty(icarus.emptyStart);
        baseData.removeEnemyBase(icarus.emptyStart, EnemyMainClearReason.NO_BUILDING_SEEN);

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getEnemyNaturalBase());
        assertTrue(offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, inEmptyStartArea));
        assertFalse(offer(UnitType.Protoss_Gateway, LV28400NFixture.SECOND_PROXY_GATEWAY, inEmptyStartArea));
        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.LAST_START, baseData.getMainEnemyBaseEvidence());
        assertEquals(Arrays.asList(
                "ENEMY_MAIN_ASSIGNED [8, 77] MAIN_AREA Protoss_Pylon [384, 1696]",
                "ENEMY_MAIN_CLEARED [8, 77] NO_BUILDING_SEEN",
                "ENEMY_MAIN_ASSIGNED [116, 47] LAST_START Protoss_Pylon [384, 1696]"), events);
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

    @Test
    void aForgeAndCannonsAtAnUnscoutedStartsNaturalSetTheMainAndItsNatural() {
        Predicate<Base> inRealNaturalArea = base -> base == icarus.realNatural;

        assertTrue(offer(UnitType.Protoss_Forge, FORGE_AT_REAL_NATURAL, inRealNaturalArea));
        assertFalse(offer(UnitType.Protoss_Photon_Cannon, CANNON_AT_REAL_NATURAL, inRealNaturalArea));
        assertFalse(offer(UnitType.Protoss_Photon_Cannon, SECOND_CANNON_AT_REAL_NATURAL, inRealNaturalArea));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.NATURAL_AREA, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertTrue(baseData.isEnemyMainBaseFound());
        assertEquals(Collections.singletonList(
                "ENEMY_MAIN_ASSIGNED [116, 47] NATURAL_AREA Protoss_Forge [3328, 1440]"), events);
    }

    @Test
    void aBunkerAtAnUnscoutedStartsNaturalSetsTheMain() {
        assertTrue(offer(UnitType.Terran_Bunker, CANNON_AT_REAL_NATURAL, base -> base == icarus.realNatural));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.NATURAL_AREA, baseData.getMainEnemyBaseEvidence());
    }

    @Test
    void theLv28400nProxyGatewayStillSetsNoMainInTheOpenOrAtAStartsNatural() {
        assertFalse(offer(UnitType.Protoss_Gateway, LV28400NFixture.PROXY_GATEWAY, IN_NO_START_AREA));
        assertFalse(offer(UnitType.Protoss_Gateway, LV28400NFixture.PROXY_GATEWAY,
                base -> base == icarus.emptyStartNatural));

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getEnemyNaturalBase());
        assertFalse(baseData.isEnemyMainBaseFound());
        assertTrue(events.isEmpty());
    }

    @Test
    void aForgeAtOurNaturalSetsNoMain() {
        assertFalse(offer(UnitType.Protoss_Forge, new Position(1440, 704), base -> base == icarus.ourNatural));

        assertNull(baseData.getMainEnemyBase());
    }

    @Test
    void aForgeStandingInTwoStartsNaturalAreasSetsNoMain() {
        assertFalse(offer(UnitType.Protoss_Forge, FORGE_AT_REAL_NATURAL,
                base -> base == icarus.realNatural || base == icarus.emptyStartNatural));

        assertNull(baseData.getMainEnemyBase());
    }

    @Test
    void aDepotOnTheWalledStartUpgradesItsEvidenceWithoutANewAssignment() {
        offer(UnitType.Protoss_Forge, FORGE_AT_REAL_NATURAL, base -> base == icarus.realNatural);

        assertFalse(offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.DEPOT, baseData.getMainEnemyBaseEvidence());
        assertEquals(1, events.size());
    }

    @Test
    void aDepotAtAnotherStartReplacesANaturalWallMainAndTheNaturalFollows() {
        offer(UnitType.Protoss_Forge, new Position(320, 2000), base -> base == icarus.emptyStartNatural);
        assertSame(icarus.emptyStart, baseData.getMainEnemyBase());
        assertSame(icarus.emptyStartNatural, baseData.getEnemyNaturalBase());

        assertTrue(offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.DEPOT, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertFalse(baseData.getEnemyBases().contains(icarus.emptyStart));
        assertEquals(Arrays.asList(
                "ENEMY_MAIN_ASSIGNED [8, 77] NATURAL_AREA Protoss_Forge [320, 2000]",
                "ENEMY_MAIN_CLEARED [8, 77] REPLACED_BY_DEPOT",
                "ENEMY_MAIN_ASSIGNED [116, 47] DEPOT Protoss_Nexus [3776, 1552]"), events);
    }

    @Test
    void aBuildingInAnotherStartsAreaReplacesANaturalWallMain() {
        offer(UnitType.Protoss_Forge, new Position(320, 2000), base -> base == icarus.emptyStartNatural);

        assertTrue(offer(UnitType.Protoss_Pylon, new Position(3584, 1424), base -> base == icarus.realMain));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.MAIN_AREA, baseData.getMainEnemyBaseEvidence());
        assertEquals("ENEMY_MAIN_CLEARED [8, 77] REPLACED_BY_STRONGER_EVIDENCE", events.get(1));
    }

    @Test
    void aNaturalWallDoesNotReplaceAMainFromItsArea() {
        offer(UnitType.Protoss_Pylon, new Position(3584, 1424), base -> base == icarus.realMain);

        assertFalse(offer(UnitType.Protoss_Forge, new Position(320, 2000), base -> base == icarus.emptyStartNatural));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.MAIN_AREA, baseData.getMainEnemyBaseEvidence());
    }

    @Test
    void anyBuildingSetsTheOnlyOtherStartNotSeenEmpty() {
        baseData.markStartSeenEmpty(icarus.emptyStart);

        assertTrue(offer(UnitType.Protoss_Gateway, LV28400NFixture.PROXY_GATEWAY, IN_NO_START_AREA));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.LAST_START, baseData.getMainEnemyBaseEvidence());
        assertSame(icarus.realNatural, baseData.getEnemyNaturalBase());
        assertEquals(Collections.singletonList(
                "ENEMY_MAIN_ASSIGNED [116, 47] LAST_START Protoss_Gateway [416, 1616]"), events);
    }

    @Test
    void aWallAtASeenEmptyStartsNaturalGivesWayToTheLastStart() {
        baseData.markStartSeenEmpty(icarus.emptyStart);

        assertTrue(offer(UnitType.Protoss_Forge, new Position(320, 2000), base -> base == icarus.emptyStartNatural));

        assertSame(icarus.realMain, baseData.getMainEnemyBase());
        assertEquals(EnemyMainEvidence.LAST_START, baseData.getMainEnemyBaseEvidence());
    }

    @Test
    void aNaturalWallUpgradesToLastStartOnceTheOtherStartIsSeenEmpty() {
        offer(UnitType.Protoss_Forge, FORGE_AT_REAL_NATURAL, base -> base == icarus.realNatural);
        baseData.markStartSeenEmpty(icarus.emptyStart);

        assertFalse(offer(UnitType.Protoss_Forge, FORGE_AT_REAL_NATURAL, base -> base == icarus.realNatural));

        assertEquals(EnemyMainEvidence.LAST_START, baseData.getMainEnemyBaseEvidence());
        assertEquals(1, events.size());
    }

    @Test
    void withTwoOtherStartsNotSeenEmptyABuildingOutsideEveryAreaSetsNoMain() {
        assertFalse(offer(UnitType.Protoss_Pylon, LV28400NFixture.PROXY_PYLON, IN_NO_START_AREA));

        assertNull(baseData.getMainEnemyBase());
    }

    /**
     * A razed main is cleared and later seen empty. The start its depot stood on is still where the enemy began,
     * so neither the last start nor a wall at another start's natural takes the main.
     */
    @Test
    void onceADepotHasBeenSeenNoWeakEvidenceSetsTheMain() {
        offer(UnitType.Protoss_Nexus, LV28400NFixture.REAL_NEXUS, IN_NO_START_AREA);
        baseData.removeEnemyBase(icarus.realMain, EnemyMainClearReason.DEPOT_DESTROYED);
        baseData.markStartSeenEmpty(icarus.realMain);

        assertFalse(offer(UnitType.Protoss_Pylon, new Position(2000, 2000), IN_NO_START_AREA));
        assertFalse(offer(UnitType.Protoss_Forge, new Position(320, 2000), base -> base == icarus.emptyStartNatural));

        assertNull(baseData.getMainEnemyBase());
        assertNull(baseData.getEnemyNaturalBase());
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
