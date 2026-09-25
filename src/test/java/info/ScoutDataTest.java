package info;

import bwapi.Race;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.Base;
import bwem.BaseFixture;
import org.junit.jupiter.api.Test;
import telemetry.PlanEvents;
import util.Time;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutDataTest {

    /**
     * One Gateway site on the first tile seen, so these cases turn on the share of the main alone.
     */
    private static final List<TilePosition> SITE_AT_DEPOT = Collections.singletonList(new TilePosition(0, 0));

    private static Collection<UnitType> of(UnitType... types) {
        return Arrays.asList(types);
    }

    @Test
    void terranStopsOnMarine() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Terran, of(UnitType.Terran_Marine)));
    }

    @Test
    void terranStopsOnBarracks() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Terran, of(UnitType.Terran_Barracks)));
    }

    @Test
    void terranContinuesOnScv() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Terran, of(UnitType.Terran_SCV)));
    }

    @Test
    void protossStopsOnDragoon() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Protoss, of(UnitType.Protoss_Dragoon)));
    }

    @Test
    void protossStopsOnCyberneticsCore() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Protoss, of(UnitType.Protoss_Cybernetics_Core)));
    }

    @Test
    void protossContinuesOnProbe() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Protoss, of(UnitType.Protoss_Probe)));
    }

    @Test
    void protossContinuesOnZealot() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Protoss, of(UnitType.Protoss_Zealot)));
    }

    @Test
    void zergStopsOnSpire() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Spire)));
    }

    @Test
    void zergStopsOnMutalisk() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Mutalisk)));
    }

    @Test
    void zergStopsOnHydralisk() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Hydralisk)));
    }

    @Test
    void zergStopsOnHydraliskDen() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Hydralisk_Den)));
    }

    @Test
    void zergStopsOnScourgeAndLurker() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Scourge)));
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Lurker)));
    }

    @Test
    void zergContinuesOnZergling() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Zergling)));
    }

    @Test
    void zergContinuesOnOverlordAndDrone() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Zerg, of(UnitType.Zerg_Overlord, UnitType.Zerg_Drone)));
    }

    @Test
    void unknownContinuesOnEmptySet() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Unknown, Collections.emptyList()));
    }

    @Test
    void unknownContinuesOnZealot() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Unknown, of(UnitType.Protoss_Zealot)));
    }

    @Test
    void unknownStopsOnMarine() {
        ScoutData scoutData = new ScoutData();
        assertFalse(scoutData.shouldOverlordsContinueScouting(Race.Unknown, of(UnitType.Terran_Marine)));
    }

    @Test
    void emptySetContinuesForTerran() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Terran, Collections.emptyList()));
    }

    @Test
    void emptySetContinuesForProtoss() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Protoss, Collections.emptyList()));
    }

    @Test
    void emptySetContinuesForZerg() {
        ScoutData scoutData = new ScoutData();
        assertTrue(scoutData.shouldOverlordsContinueScouting(Race.Zerg, Collections.emptyList()));
    }

    /**
     * A bwem Base cannot be built outside its package, so the null key stands in for one enemy main.
     */
    @Test
    void anEnemyMainNeverSeenIsNotScouted() {
        assertNull(new ScoutData().getEnemyMainScoutedFrame(null));
    }

    @Test
    void seeingOnlyTheDepotDoesNotScoutTheMain() {
        ScoutData scoutData = new ScoutData();

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 12), 100, SITE_AT_DEPOT, new Time(3314));

        assertNull(scoutData.getEnemyMainScoutedFrame(null));
        assertTrue(scoutData.hasSeenEnemyMainTile(null, new TilePosition(0, 0)));
        assertFalse(scoutData.hasSeenEnemyMainTile(null, new TilePosition(12, 0)));
    }

    @Test
    void visionAccumulatedOverFramesScoutsTheMainOnceItCoversTheThreshold() {
        ScoutData scoutData = new ScoutData();

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 30), 100, SITE_AT_DEPOT, new Time(3314));
        scoutData.recordEnemyMainVision(null, tilesInRow(20, 49), 100, SITE_AT_DEPOT, new Time(3400));
        assertNull(scoutData.getEnemyMainScoutedFrame(null));

        scoutData.recordEnemyMainVision(null, tilesInRow(49, 50), 100, SITE_AT_DEPOT, new Time(3490));
        scoutData.recordEnemyMainVision(null, tilesInRow(50, 100), 100, SITE_AT_DEPOT, new Time(3600));

        assertEquals(new Time(3490), scoutData.getEnemyMainScoutedFrame(null));
    }

    /**
     * GAME_LU01I0JS, close spawns on (4)Python: the enemy Nexus was in our vision on frame 1373, before any home
     * Gateway stood, and never again before 5:00.
     */
    @Test
    void visionBeforeTheStartIsIgnored() {
        ScoutData scoutData = new ScoutData();

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 100), 100, SITE_AT_DEPOT, new Time(1373));

        assertNull(scoutData.getEnemyMainScoutedFrame(null));
        assertFalse(scoutData.hasSeenEnemyMainTile(null, new TilePosition(0, 0)));

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 100), 100, SITE_AT_DEPOT,
                ScoutData.ENEMY_MAIN_VISION_START);

        assertEquals(ScoutData.ENEMY_MAIN_VISION_START, scoutData.getEnemyMainScoutedFrame(null));
    }

    @Test
    void halfTheMainSeenWithoutTheGroundAroundTheDepotIsNotScouted() {
        ScoutData scoutData = new ScoutData();
        List<TilePosition> gatewaySites = tilesInRow(80, 100);

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 80), 100, gatewaySites, new Time(3314));

        assertNull(scoutData.getEnemyMainScoutedFrame(null));
    }

    @Test
    void theMainIsScoutedOnceTheGatewaySitesAreSeenToo() {
        ScoutData scoutData = new ScoutData();
        List<TilePosition> gatewaySites = tilesInRow(80, 100);

        scoutData.recordEnemyMainVision(null, tilesInRow(0, 80), 100, gatewaySites, new Time(3314));
        scoutData.recordEnemyMainVision(null, tilesInRow(80, 97), 100, gatewaySites, new Time(3400));
        assertNull(scoutData.getEnemyMainScoutedFrame(null));

        scoutData.recordEnemyMainVision(null, tilesInRow(97, 98), 100, gatewaySites, new Time(3490));

        assertEquals(new Time(3490), scoutData.getEnemyMainScoutedFrame(null));
    }

    @Test
    void theScoutedThresholdIsHalfTheMainAndNineTenthsOfItsGatewaySites() {
        assertEquals(0.5, ScoutData.ENEMY_MAIN_SCOUTED_COVERAGE);
        assertEquals(0.9, ScoutData.ENEMY_MAIN_GATEWAY_SITE_COVERAGE);
        assertFalse(ScoutData.isScouted(49, 100, 10, 10));
        assertTrue(ScoutData.isScouted(50, 100, 9, 10));
        assertFalse(ScoutData.isScouted(100, 100, 8, 10));
        assertFalse(ScoutData.isScouted(0, 0, 0, 0));
    }

    @Test
    void aMainWithNoGatewaySitesIsNeverScouted() {
        assertFalse(ScoutData.isScouted(100, 100, 0, 0));
    }

    /**
     * Home Gateways and Cores in batch 20260923-121254 stood up to 396 px, about 12.4 tiles, from the depot.
     */
    @Test
    void gatewaySitesReachFourteenTilesFromTheDepot() {
        TilePosition depot = new TilePosition(100, 100);

        assertTrue(ScoutData.isGatewaySite(new TilePosition(100, 100), depot));
        assertTrue(ScoutData.isGatewaySite(new TilePosition(112, 106), depot));
        assertTrue(ScoutData.isGatewaySite(new TilePosition(114, 100), depot));
        assertFalse(ScoutData.isGatewaySite(new TilePosition(115, 100), depot));
        assertFalse(ScoutData.isGatewaySite(new TilePosition(111, 111), depot));
    }

    @Test
    void theFirstScoutedFrameOfAnEnemyMainIsReportedOnce() {
        Base enemyMain = BaseFixture.startingLocation(new TilePosition(116, 47));
        List<String> events = new ArrayList<>();
        PlanEvents.register(new EnemyMainEventRecorder(events));
        try {
            ScoutData scoutData = new ScoutData();

            scoutData.recordEnemyMainVision(enemyMain, tilesInRow(0, 30), 100, SITE_AT_DEPOT, new Time(3314));
            assertTrue(events.isEmpty());

            scoutData.recordEnemyMainVision(enemyMain, tilesInRow(30, 100), 100, SITE_AT_DEPOT, new Time(3490));
            scoutData.recordEnemyMainVision(enemyMain, tilesInRow(0, 100), 100, SITE_AT_DEPOT, new Time(3600));

            assertEquals(new Time(3490), scoutData.getEnemyMainScoutedFrame(enemyMain));
            assertEquals(Collections.singletonList("ENEMY_MAIN_SCOUTED " + new TilePosition(116, 47)), events);
        } finally {
            PlanEvents.clear();
        }
    }

    private static List<TilePosition> tilesInRow(int fromX, int toX) {
        List<TilePosition> tiles = new ArrayList<>();
        for (int x = fromX; x < toX; x++) {
            tiles.add(new TilePosition(x, 0));
        }
        return tiles;
    }
}
