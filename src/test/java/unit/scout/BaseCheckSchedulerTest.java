package unit.scout;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseCheckSchedulerTest {

    private static final int NOW = BaseCheckScheduler.FIRST_CHECK_FRAME + 10000;

    private static Map<String, Integer> map(Object... pairs) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return result;
    }

    @Test
    void intervalIsOneMinuteAndNotTheHarassStrikeConstant() {
        assertEquals(1440, BaseCheckScheduler.CHECK_INTERVAL_FRAMES);
    }

    @Test
    void aBaseIsDueOnceUnseenForTheInterval() {
        assertFalse(BaseCheckScheduler.isDue(BaseCheckScheduler.CHECK_INTERVAL_FRAMES - 1));
        assertTrue(BaseCheckScheduler.isDue(BaseCheckScheduler.CHECK_INTERVAL_FRAMES));
    }

    @Test
    void aBaseNeverSeenIsTheStalestPossible() {
        assertEquals(Integer.MAX_VALUE, BaseCheckScheduler.age(-1, NOW));
        assertEquals(100, BaseCheckScheduler.age(NOW - 100, NOW));
    }

    @Test
    void picksTheStalestDueBase() {
        Map<String, Integer> lastSeen = map("a", NOW - 2000, "b", NOW - 5000, "c", NOW - 3000);
        String next = BaseCheckScheduler.next(Arrays.asList("a", "b", "c"), lastSeen, Collections.emptyMap(),
                Collections.emptyList(), NOW);
        assertEquals("b", next);
    }

    @Test
    void aBaseNeverSeenBeatsAnySeenBase() {
        Map<String, Integer> lastSeen = map("a", 0);
        String next = BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, Collections.emptyMap(),
                Collections.emptyList(), NOW);
        assertEquals("b", next);
    }

    @Test
    void tiesBreakByShorterGroundPath() {
        Map<String, Integer> lastSeen = map("a", NOW - 3000, "b", NOW - 3000);
        Map<String, Integer> distances = map("a", 900, "b", 400);
        String next = BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, distances,
                Collections.emptyList(), NOW);
        assertEquals("b", next);
    }

    @Test
    void aBaseWithoutAGroundPathSortsLastInATie() {
        Map<String, Integer> lastSeen = map("a", NOW - 3000, "b", NOW - 3000);
        Map<String, Integer> distances = map("b", 900);
        String next = BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, distances,
                Collections.emptyList(), NOW);
        assertEquals("b", next);
    }

    @Test
    void noBaseIsCheckedBeforeItIsDue() {
        Map<String, Integer> lastSeen = map("a", NOW - 1000, "b", NOW - 1439);
        assertNull(BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, Collections.emptyMap(),
                Collections.emptyList(), NOW));
    }

    @Test
    void aBaseAlreadyBeingCheckedIsSkipped() {
        Map<String, Integer> lastSeen = map("a", NOW - 5000, "b", NOW - 3000);
        String next = BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, Collections.emptyMap(),
                Collections.singletonList("a"), NOW);
        assertEquals("b", next);
    }

    @Test
    void noBaseIsCheckedBeforeTheFirstCheckFrame() {
        assertNull(BaseCheckScheduler.next(Collections.singletonList("a"), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), BaseCheckScheduler.FIRST_CHECK_FRAME - 1));
    }

    @Test
    void noCandidatesGivesNoCheck() {
        assertNull(BaseCheckScheduler.next(Collections.<String>emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), NOW));
    }

    @Test
    void twoLingsAreSentWhenSpiderMinesAreKnown() {
        assertEquals(2, BaseCheckScheduler.lingsPerCheck(true));
        assertEquals(1, BaseCheckScheduler.lingsPerCheck(false));
    }

    @Test
    void aHealthyScoutThatHasNotSeenItsBaseIsKeptOutHoweverManyScoutsAreOut() {
        assertEquals(BaseCheckScheduler.Release.NONE,
                BaseCheckScheduler.releaseReason(false, 35, 35, NOW - 100, NOW));
    }

    @Test
    void seeingTheBaseEndsTheCheck() {
        assertEquals(BaseCheckScheduler.Release.SEEN,
                BaseCheckScheduler.releaseReason(true, 35, 35, NOW - 100, NOW));
    }

    @Test
    void seeingTheBaseOutranksTheHitPointRecall() {
        assertEquals(BaseCheckScheduler.Release.SEEN,
                BaseCheckScheduler.releaseReason(true, 1, 35, NOW - 100, NOW));
    }

    @Test
    void underHalfHitPointsRecallsTheScout() {
        assertEquals(BaseCheckScheduler.Release.HP_RECALL,
                BaseCheckScheduler.releaseReason(false, 17, 35, NOW - 100, NOW));
        assertEquals(BaseCheckScheduler.Release.NONE,
                BaseCheckScheduler.releaseReason(false, 18, 35, NOW - 100, NOW));
    }

    @Test
    void aCheckThatNeverSeesItsBaseTimesOut() {
        assertEquals(BaseCheckScheduler.Release.TIMEOUT, BaseCheckScheduler.releaseReason(false, 35, 35,
                NOW - BaseCheckScheduler.CHECK_TIMEOUT_FRAMES, NOW));
    }

    @Test
    void aFailedBaseIsLeftAloneUntilItsRetryFrame() {
        Map<String, Integer> lastSeen = map("a", NOW - 5000, "b", NOW - 3000);
        int retryFrame = BaseCheckScheduler.retryFrame(NOW, 1);
        assertEquals("b", BaseCheckScheduler.next(Arrays.asList("a", "b"), lastSeen, Collections.emptyMap(),
                Collections.singletonList("a"), NOW));
        assertEquals(NOW + BaseCheckScheduler.CHECK_INTERVAL_FRAMES, retryFrame);
    }

    @Test
    void theRetryWaitDoublesWithEachFailureUpToTheCap() {
        int interval = BaseCheckScheduler.CHECK_INTERVAL_FRAMES;
        assertEquals(NOW + interval, BaseCheckScheduler.retryFrame(NOW, 1));
        assertEquals(NOW + 2 * interval, BaseCheckScheduler.retryFrame(NOW, 2));
        assertEquals(NOW + 4 * interval, BaseCheckScheduler.retryFrame(NOW, 3));
        assertEquals(NOW + BaseCheckScheduler.MAX_BACKOFF_INTERVALS * interval,
                BaseCheckScheduler.retryFrame(NOW, 40));
    }

    @Test
    void checksAreCappedPerKind() {
        assertTrue(BaseCheckScheduler.mayStartCheck(BaseCheckScheduler.MAX_LING_CHECKS - 1, false));
        assertFalse(BaseCheckScheduler.mayStartCheck(BaseCheckScheduler.MAX_LING_CHECKS, false));
        assertTrue(BaseCheckScheduler.mayStartCheck(0, true));
        assertFalse(BaseCheckScheduler.mayStartCheck(BaseCheckScheduler.MAX_OVERLORD_CHECKS, true));
    }

    @Test
    void aScoutBelowTheRecallLineIsNotHealthyEnoughToSend() {
        assertFalse(BaseCheckScheduler.isHealthy(17, 35));
        assertTrue(BaseCheckScheduler.isHealthy(18, 35));
    }

    @Test
    void aZerglingScoutIsNotReleasedByTheCountOfScoutsOut() {
        assertFalse(BaseCheckScheduler.endsZerglingScout(35, 35, false));
    }

    @Test
    void aZerglingScoutEndsOnLocatingTheEnemyOrTheHitPointRecall() {
        assertTrue(BaseCheckScheduler.endsZerglingScout(35, 35, true));
        assertTrue(BaseCheckScheduler.endsZerglingScout(10, 35, false));
    }

    @Test
    void anOverlordNeedsSpeedAClearRouteAndNoVeto() {
        assertTrue(BaseCheckScheduler.overlordMayCheck(true, true, true));
        assertFalse(BaseCheckScheduler.overlordMayCheck(false, true, true));
        assertFalse(BaseCheckScheduler.overlordMayCheck(true, false, true));
        assertFalse(BaseCheckScheduler.overlordMayCheck(true, true, false));
    }

    @Test
    void aMarineBesideTheRouteBlocksItAndOneFarFromItDoesNot() {
        Position from = new Position(0, 0);
        Position to = new Position(2000, 0);
        BaseCheckScheduler.Sighting near = new BaseCheckScheduler.Sighting(UnitType.Terran_Marine,
                new Position(1000, 100));
        BaseCheckScheduler.Sighting far = new BaseCheckScheduler.Sighting(UnitType.Terran_Marine,
                new Position(1000, 1500));
        assertFalse(BaseCheckScheduler.routeClear(from, to, Collections.singletonList(near)));
        assertTrue(BaseCheckScheduler.routeClear(from, to, Collections.singletonList(far)));
    }

    @Test
    void groundUnitsThatCannotShootUpDoNotBlockTheRoute() {
        BaseCheckScheduler.Sighting zealot = new BaseCheckScheduler.Sighting(UnitType.Protoss_Zealot,
                new Position(1000, 0));
        assertTrue(BaseCheckScheduler.routeClear(new Position(0, 0), new Position(2000, 0),
                Collections.singletonList(zealot)));
    }

    @Test
    void anAirThreatBlocksTheRouteWhereverItIs() {
        BaseCheckScheduler.Sighting wraith = new BaseCheckScheduler.Sighting(UnitType.Terran_Wraith,
                new Position(1000, 3000));
        assertFalse(BaseCheckScheduler.routeClear(new Position(0, 0), new Position(2000, 0),
                Collections.singletonList(wraith)));
    }

    @Test
    void distanceToSegmentClampsToTheEndpoints() {
        assertEquals(100.0, BaseCheckScheduler.distanceToSegment(new Position(-100, 0), new Position(0, 0),
                new Position(1000, 0)), 1e-9);
        assertEquals(50.0, BaseCheckScheduler.distanceToSegment(new Position(500, 50), new Position(0, 0),
                new Position(1000, 0)), 1e-9);
    }

    @Test
    void aBaseIsOccupiedOnlyWhenAnEnemyStandsNearIt() {
        Position center = new Position(1000, 1000);
        assertTrue(BaseCheckScheduler.isOccupied(Collections.singletonList(new Position(1100, 1000)), center));
        assertFalse(BaseCheckScheduler.isOccupied(Collections.singletonList(new Position(2000, 1000)), center));
        assertFalse(BaseCheckScheduler.isOccupied(Collections.<Position>emptyList(), center));
    }

    @Test
    void anUnscoutedStartLocationBeatsAnEqualNeverSeenExpansionEvenWhenFarther() {
        Map<String, Integer> distances = map("expansion", 100, "start", 900);
        String next = BaseCheckScheduler.next(Arrays.asList("expansion", "start"), Collections.emptyMap(),
                distances, Collections.emptyList(), Collections.singleton("start"), NOW);
        assertEquals("start", next);
    }

    @Test
    void anUnscoutedStartLocationBeatsAStaleSeenBase() {
        Map<String, Integer> lastSeen = map("stale", 0);
        String next = BaseCheckScheduler.next(Arrays.asList("stale", "start"), lastSeen, Collections.emptyMap(),
                Collections.emptyList(), Collections.singleton("start"), NOW);
        assertEquals("start", next);
    }

    @Test
    void aStartLocationAlreadySeenIsOrderedByStalenessLikeAnyOtherBase() {
        Map<String, Integer> lastSeen = map("start", NOW - 2000, "expansion", NOW - 5000);
        String next = BaseCheckScheduler.next(Arrays.asList("start", "expansion"), lastSeen,
                Collections.emptyMap(), Collections.emptyList(), Collections.singleton("start"), NOW);
        assertEquals("expansion", next);
    }

    @Test
    void anUnscoutedStartLocationAlreadyBeingCheckedIsSkipped() {
        String next = BaseCheckScheduler.next(Arrays.asList("expansion", "start"), Collections.emptyMap(),
                Collections.emptyMap(), Collections.singletonList("start"), Collections.singleton("start"), NOW);
        assertEquals("expansion", next);
    }

    @Test
    void anUnscoutedStartLocationWaitsForTheFirstCheckFrame() {
        assertNull(BaseCheckScheduler.next(Collections.singletonList("start"), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyList(), Collections.singleton("start"),
                BaseCheckScheduler.FIRST_CHECK_FRAME - 1));
    }

    @Test
    void aHeldBaseIsNotDispatchedToWhileSeenWithinTheInterval() {
        assertFalse(BaseCheckScheduler.mayDispatchToHeldBase(BaseCheckScheduler.CHECK_INTERVAL_FRAMES - 1, 0, NOW));
        assertTrue(BaseCheckScheduler.mayDispatchToHeldBase(BaseCheckScheduler.CHECK_INTERVAL_FRAMES, 0, NOW));
        assertTrue(BaseCheckScheduler.mayDispatchToHeldBase(Integer.MAX_VALUE, 0, NOW));
    }

    @Test
    void aHeldBaseIsNotDispatchedToBeforeItsRetryFrame() {
        assertFalse(BaseCheckScheduler.mayDispatchToHeldBase(Integer.MAX_VALUE, NOW + 1, NOW));
        assertTrue(BaseCheckScheduler.mayDispatchToHeldBase(Integer.MAX_VALUE, NOW, NOW));
    }

    @Test
    void aRoutePassingNearADeathSiteIsAvoided() {
        Position death = new Position(500, 500);
        List<Position> route = Arrays.asList(new Position(0, 0),
                new Position(500, 500 + BaseCheckScheduler.DEATH_AVOID_RADIUS_PIXELS));
        assertTrue(BaseCheckScheduler.routePassesDeathSite(route, Collections.singletonList(death)));
    }

    @Test
    void aRouteFarFromEveryDeathSiteIsNotAvoided() {
        Position death = new Position(500, 500);
        List<Position> route = Arrays.asList(new Position(0, 0),
                new Position(500, 500 + BaseCheckScheduler.DEATH_AVOID_RADIUS_PIXELS + 1));
        assertFalse(BaseCheckScheduler.routePassesDeathSite(route, Collections.singletonList(death)));
        assertFalse(BaseCheckScheduler.routePassesDeathSite(route, Collections.emptyList()));
        assertFalse(BaseCheckScheduler.routePassesDeathSite(Collections.emptyList(),
                Collections.singletonList(death)));
    }

    private static BaseCheckScheduler.Sighting sighting(UnitType type, int x, int y) {
        return new BaseCheckScheduler.Sighting(type, new Position(x, y));
    }

    @Test
    void aDeathBesideAStaticDefenceIsAnchoredOnTheDefence() {
        Position anchor = BaseCheckScheduler.deathSiteAnchor(new Position(500, 500),
                Arrays.asList(sighting(UnitType.Terran_Marine, 500, 490), sighting(UnitType.Terran_Bunker, 600, 500)));
        assertEquals(new Position(600, 500), anchor);
    }

    @Test
    void aDeathNearTheNearestOfSeveralDefencesIsAnchoredOnThatOne() {
        Position anchor = BaseCheckScheduler.deathSiteAnchor(new Position(500, 500),
                Arrays.asList(sighting(UnitType.Zerg_Sunken_Colony, 700, 500),
                        sighting(UnitType.Protoss_Photon_Cannon, 560, 500)));
        assertEquals(new Position(560, 500), anchor);
    }

    @Test
    void aDeathWithNoStaticDefenceNearIsNotRemembered() {
        assertNull(BaseCheckScheduler.deathSiteAnchor(new Position(500, 500),
                Arrays.asList(sighting(UnitType.Terran_Marine, 510, 500), sighting(UnitType.Terran_Bunker, 5000, 500))));
        assertNull(BaseCheckScheduler.deathSiteAnchor(new Position(500, 500), Collections.emptyList()));
    }

    @Test
    void aDefenceJustOutsideTheAvoidRadiusDoesNotAnchorADeath() {
        int distance = BaseCheckScheduler.DEATH_AVOID_RADIUS_PIXELS;
        assertEquals(new Position(500 + distance, 500), BaseCheckScheduler.deathSiteAnchor(new Position(500, 500),
                Collections.singletonList(sighting(UnitType.Terran_Bunker, 500 + distance, 500))));
        assertNull(BaseCheckScheduler.deathSiteAnchor(new Position(500, 500),
                Collections.singletonList(sighting(UnitType.Terran_Bunker, 500 + distance + 1, 500))));
    }

    @Test
    void onlyBunkersCannonsAndSunkensAreStaticDefence() {
        assertTrue(BaseCheckScheduler.isStaticDefence(UnitType.Terran_Bunker));
        assertTrue(BaseCheckScheduler.isStaticDefence(UnitType.Protoss_Photon_Cannon));
        assertTrue(BaseCheckScheduler.isStaticDefence(UnitType.Zerg_Sunken_Colony));
        assertFalse(BaseCheckScheduler.isStaticDefence(UnitType.Terran_Marine));
        assertFalse(BaseCheckScheduler.isStaticDefence(UnitType.Zerg_Spore_Colony));
    }

    @Test
    void lingCheckCapIsThree() {
        assertTrue(BaseCheckScheduler.mayStartCheck(2, false));
        assertFalse(BaseCheckScheduler.mayStartCheck(3, false));
    }

    @Test
    void aDeathIsRememberedForExactlyTheMemoryWindowOnceItsDefenceIsGone() {
        assertTrue(BaseCheckScheduler.isDeathRemembered(1000, 1000 + BaseCheckScheduler.DEATH_MEMORY_FRAMES - 1,
                false));
        assertFalse(BaseCheckScheduler.isDeathRemembered(1000, 1000 + BaseCheckScheduler.DEATH_MEMORY_FRAMES,
                false));
    }

    @Test
    void aDeathIsRememberedWithoutLimitWhileItsDefenceStands() {
        assertTrue(BaseCheckScheduler.isDeathRemembered(1000, 1000 + 10 * BaseCheckScheduler.DEATH_MEMORY_FRAMES,
                true));
    }

    @Test
    void anAnchorIsAliveOnlyWhileAStaticDefenceStandsOnIt() {
        Position anchor = new Position(600, 500);
        assertTrue(BaseCheckScheduler.isAnchorAlive(anchor,
                Collections.singletonList(sighting(UnitType.Terran_Bunker, 600, 500))));
        assertTrue(BaseCheckScheduler.isAnchorAlive(anchor, Collections.singletonList(
                sighting(UnitType.Terran_Bunker, 600 + BaseCheckScheduler.ANCHOR_MATCH_PIXELS, 500))));
        assertFalse(BaseCheckScheduler.isAnchorAlive(anchor, Collections.singletonList(
                sighting(UnitType.Terran_Bunker, 600 + BaseCheckScheduler.ANCHOR_MATCH_PIXELS + 1, 500))));
        assertFalse(BaseCheckScheduler.isAnchorAlive(anchor,
                Collections.singletonList(sighting(UnitType.Terran_Marine, 600, 500))));
        assertFalse(BaseCheckScheduler.isAnchorAlive(anchor, Collections.emptyList()));
    }

    @Test
    void staticDefencePositionsKeepOnlyDefences() {
        List<Position> positions = BaseCheckScheduler.staticDefencePositions(Arrays.asList(
                sighting(UnitType.Terran_Marine, 1, 1), sighting(UnitType.Terran_Bunker, 2, 2),
                sighting(UnitType.Zerg_Sunken_Colony, 3, 3)));
        assertEquals(Arrays.asList(new Position(2, 2), new Position(3, 3)), positions);
    }

    @Test
    void twoRoutesPastTheSameDefenceShareIt() {
        Position defence = new Position(1000, 1000);
        List<Position> first = Arrays.asList(new Position(0, 0), new Position(1000, 1100));
        List<Position> second = Arrays.asList(new Position(0, 500), new Position(900, 1000));
        assertEquals(defence, BaseCheckScheduler.sharedDefence(first, second, Collections.singletonList(defence)));
    }

    @Test
    void routesPastDifferentDefencesShareNone() {
        List<Position> defences = Arrays.asList(new Position(1000, 1000), new Position(5000, 5000));
        List<Position> first = Collections.singletonList(new Position(1000, 1100));
        List<Position> second = Collections.singletonList(new Position(5000, 5100));
        assertNull(BaseCheckScheduler.sharedDefence(first, second, defences));
    }

    @Test
    void aRoutePastNoDefenceSharesNone() {
        List<Position> route = Collections.singletonList(new Position(0, 0));
        assertNull(BaseCheckScheduler.sharedDefence(route, route,
                Collections.singletonList(new Position(1000, 1000))));
        assertNull(BaseCheckScheduler.sharedDefence(route, route, Collections.emptyList()));
    }

    @Test
    void defencesNearARouteAreThoseWithinTheAvoidRadius() {
        List<Position> route = Collections.singletonList(new Position(0, 0));
        Position near = new Position(BaseCheckScheduler.DEATH_AVOID_RADIUS_PIXELS, 0);
        Position far = new Position(BaseCheckScheduler.DEATH_AVOID_RADIUS_PIXELS + 1, 0);
        assertEquals(Collections.singletonList(near),
                BaseCheckScheduler.defencesNearRoute(route, Arrays.asList(near, far)));
    }

    @Test
    void theDeathSiteOnARouteIsTheFirstOneWithinTheAvoidRadius() {
        List<Position> route = Collections.singletonList(new Position(0, 0));
        Position far = new Position(5000, 0);
        Position near = new Position(100, 0);
        assertEquals(near, BaseCheckScheduler.deathSiteOnRoute(route, Arrays.asList(far, near)));
        assertNull(BaseCheckScheduler.deathSiteOnRoute(route, Collections.singletonList(far)));
    }

    @Test
    void theProbeStartsAtTenMinutes() {
        assertEquals(14400, BaseCheckScheduler.PERIODIC_PROBE_START_FRAME);
    }

    @Test
    void beforeTheProbeStartsOnlyAnUnscoutedStartIsCheckableWhileTheEnemyMainIsUnknown() {
        Map<String, Integer> lastSeen = map("seenStart", 100, "stale", 0);
        List<String> checkable = BaseCheckScheduler.checkable(
                Arrays.asList("start", "seenStart", "expansion", "stale"), lastSeen,
                Arrays.asList("start", "seenStart"), false, BaseCheckScheduler.PERIODIC_PROBE_START_FRAME - 1);
        assertEquals(Collections.singletonList("start"), checkable);
    }

    @Test
    void beforeTheProbeStartsNothingIsCheckableOnceTheEnemyMainIsKnown() {
        assertTrue(BaseCheckScheduler.checkable(Arrays.asList("start", "expansion"), Collections.emptyMap(),
                Collections.singleton("start"), true, BaseCheckScheduler.PERIODIC_PROBE_START_FRAME - 1).isEmpty());
    }

    @Test
    void fromTheProbeStartEveryCandidateIsCheckable() {
        List<String> all = Arrays.asList("start", "seenStart", "expansion");
        assertEquals(all, BaseCheckScheduler.checkable(all, map("seenStart", 5), Collections.singleton("start"),
                true, BaseCheckScheduler.PERIODIC_PROBE_START_FRAME));
        assertEquals(all, BaseCheckScheduler.checkable(all, map("seenStart", 5), Collections.singleton("start"),
                false, BaseCheckScheduler.PERIODIC_PROBE_START_FRAME));
    }

    @Test
    void anEnemyMainNeverSeenMustBeFoundOnceChecksAreAllowed() {
        assertTrue(BaseCheckScheduler.mustFindEnemyMain(-1, BaseCheckScheduler.FIRST_CHECK_FRAME));
        assertFalse(BaseCheckScheduler.mustFindEnemyMain(-1, BaseCheckScheduler.FIRST_CHECK_FRAME - 1));
        assertFalse(BaseCheckScheduler.mustFindEnemyMain(0, NOW));
    }

    @Test
    void aSiteNearerTheBaseThanTheScoutIsAhead() {
        Position base = new Position(3000, 0);
        assertTrue(BaseCheckScheduler.isSiteAhead(new Position(0, 0), base, new Position(1500, 0)));
    }

    @Test
    void aSiteTheScoutHasPassedIsNotAhead() {
        Position base = new Position(3000, 0);
        assertFalse(BaseCheckScheduler.isSiteAhead(new Position(2000, 0), base, new Position(1500, 0)));
    }

    @Test
    void aScoutOfUnknownPositionIsRecalledFromAnySite() {
        assertTrue(BaseCheckScheduler.isSiteAhead(null, new Position(3000, 0), new Position(1500, 0)));
    }

    @Test
    void aSiteWhoseDefenceHasFallenExpiresAtTheMemoryWindowEvenForTheMainSearch() {
        assertTrue(BaseCheckScheduler.isDeathRemembered(0, BaseCheckScheduler.DEATH_MEMORY_FRAMES - 1, false));
        assertFalse(BaseCheckScheduler.isDeathRemembered(0, BaseCheckScheduler.DEATH_MEMORY_FRAMES, false));
    }
}
