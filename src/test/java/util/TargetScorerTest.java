package util;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetScorerTest {

    private static final boolean MUTALISK = UnitType.Zerg_Mutalisk.isFlyer();
    private static final boolean ZERGLING = UnitType.Zerg_Zergling.isFlyer();

    private static TargetScorer.Candidate at(UnitType type, int distance) {
        return new TargetScorer.Candidate(type, distance, 1.0, false, false);
    }

    private static TargetScorer.Candidate currentAt(UnitType type, int distance) {
        return new TargetScorer.Candidate(type, distance, 1.0, true, false);
    }

    private static UnitType chosen(boolean attackerIsFlying, TargetScorer.Candidate... candidates) {
        List<TargetScorer.Candidate> list = Arrays.asList(candidates);
        return list.get(TargetScorer.selectIndex(attackerIsFlying, list)).type();
    }

    @Test
    void theAttackersAreClassifiedByTheirOwnUnitTypes() {
        assertTrue(MUTALISK);
        assertFalse(ZERGLING);
    }

    @Test
    void aMutaliskPrefersAnOverlordOverANearerSunkenColony() {
        assertEquals(UnitType.Zerg_Overlord,
                chosen(MUTALISK, at(UnitType.Zerg_Sunken_Colony, 50), at(UnitType.Zerg_Overlord, 250)));
    }

    @Test
    void aMutaliskLeavesTheSunkenColonyItIsAlreadyShootingForAFartherOverlord() {
        assertEquals(UnitType.Zerg_Overlord,
                chosen(MUTALISK, currentAt(UnitType.Zerg_Sunken_Colony, 20), at(UnitType.Zerg_Overlord, 256)));
    }

    @Test
    void aMutaliskRanksDroneThenOverlordThenSunkenColony() {
        assertEquals(TargetScorer.Priority.ELEVATED,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, MUTALISK, false));
        assertEquals(TargetScorer.Priority.NORMAL,
                TargetScorer.assignPriority(UnitType.Zerg_Overlord, MUTALISK, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, MUTALISK, false));
        assertEquals(UnitType.Zerg_Drone, chosen(MUTALISK,
                at(UnitType.Zerg_Sunken_Colony, 30), at(UnitType.Zerg_Overlord, 60), at(UnitType.Zerg_Drone, 250)));
    }

    @Test
    void aMutaliskRanksASunkenColonyLevelWithAHatchery() {
        assertEquals(TargetScorer.assignPriority(UnitType.Zerg_Hatchery, MUTALISK, false),
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, MUTALISK, false));
    }

    @Test
    void aMutaliskStillPrefersAnythingThatCanShootIt() {
        List<UnitType> shooters = Arrays.asList(
                UnitType.Zerg_Spore_Colony,
                UnitType.Terran_Missile_Turret,
                UnitType.Protoss_Photon_Cannon,
                UnitType.Terran_Bunker,
                UnitType.Zerg_Hydralisk,
                UnitType.Zerg_Mutalisk,
                UnitType.Terran_Marine,
                UnitType.Terran_Goliath,
                UnitType.Protoss_Dragoon,
                UnitType.Protoss_Corsair,
                UnitType.Protoss_Carrier);
        List<UnitType> harmless = Arrays.asList(
                UnitType.Zerg_Drone,
                UnitType.Zerg_Overlord,
                UnitType.Zerg_Sunken_Colony,
                UnitType.Zerg_Hatchery,
                UnitType.Zerg_Zergling);

        for (UnitType shooter : shooters) {
            assertEquals(TargetScorer.Priority.CRITICAL, TargetScorer.assignPriority(shooter, MUTALISK, false),
                    shooter.toString());
            for (UnitType other : harmless) {
                assertEquals(shooter, chosen(MUTALISK, at(other, 10), at(shooter, 250)),
                        shooter + " vs " + other);
            }
        }
    }

    @Test
    void aMutaliskStillPrefersAnAttackingWorker() {
        TargetScorer.Candidate meanDrone = new TargetScorer.Candidate(UnitType.Zerg_Drone, 250, 1.0, false, true);
        List<TargetScorer.Candidate> list = Arrays.asList(at(UnitType.Zerg_Drone, 10), meanDrone);

        assertEquals(1, TargetScorer.selectIndex(MUTALISK, list));
    }

    @Test
    void aZerglingStillPrefersAGroundThreatOverAWorkerAndAWorkerOverABuilding() {
        assertEquals(UnitType.Zerg_Sunken_Colony,
                chosen(ZERGLING, at(UnitType.Zerg_Drone, 10), at(UnitType.Zerg_Sunken_Colony, 250)));
        assertEquals(UnitType.Protoss_Photon_Cannon,
                chosen(ZERGLING, at(UnitType.Protoss_Probe, 10), at(UnitType.Protoss_Photon_Cannon, 250)));
        assertEquals(UnitType.Terran_Bunker,
                chosen(ZERGLING, at(UnitType.Terran_SCV, 10), at(UnitType.Terran_Bunker, 250)));
        assertEquals(UnitType.Zerg_Zergling,
                chosen(ZERGLING, at(UnitType.Zerg_Drone, 10), at(UnitType.Zerg_Zergling, 250)));
        assertEquals(UnitType.Zerg_Drone,
                chosen(ZERGLING, at(UnitType.Zerg_Hatchery, 10), at(UnitType.Zerg_Drone, 250)));
        assertEquals(UnitType.Zerg_Sunken_Colony,
                chosen(ZERGLING, at(UnitType.Zerg_Spore_Colony, 10), at(UnitType.Zerg_Sunken_Colony, 250)));
    }

    @Test
    void aZerglingKeepsTheTiersItHadForGroundThreatsWorkersAndBuildings() {
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Protoss_Photon_Cannon, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Terran_Bunker, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Terran_Marine, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, ZERGLING, true));
        assertEquals(TargetScorer.Priority.ELEVATED,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, ZERGLING, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Zerg_Hatchery, ZERGLING, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Terran_Supply_Depot, ZERGLING, false));
    }

    @Test
    void withinATierTheNearerCandidateWins() {
        assertEquals(UnitType.Zerg_Hatchery,
                chosen(ZERGLING, at(UnitType.Zerg_Lair, 200), at(UnitType.Zerg_Hatchery, 100)));
    }

    @Test
    void withinATierTheCurrentTargetHoldsUntilAnotherIsMoreThanTwentyPercentCloser() {
        assertEquals(UnitType.Zerg_Lair,
                chosen(ZERGLING, currentAt(UnitType.Zerg_Lair, 115), at(UnitType.Zerg_Hatchery, 100)));
        assertEquals(UnitType.Zerg_Hatchery,
                chosen(ZERGLING, currentAt(UnitType.Zerg_Lair, 130), at(UnitType.Zerg_Hatchery, 100)));
    }

    @Test
    void aZeroDistanceCandidateScoresAsOnePixelAway() {
        assertEquals(at(UnitType.Zerg_Drone, 1).score(ZERGLING), at(UnitType.Zerg_Drone, 0).score(ZERGLING));
    }

    private static TargetScorer.Candidate lingOn(UnitType type, int distance, int assigned) {
        return new TargetScorer.Candidate(type, distance, 1.0, false, false)
                .withMeleeLoad(assigned, TargetScorer.loadCap(UnitType.Zerg_Zergling, type));
    }

    @Test
    void twentyLingsSpreadOverThreeMarinesAndTwoMedicsWithoutExceedingAnyCap() {
        UnitType[] types = {UnitType.Terran_Marine, UnitType.Terran_Marine, UnitType.Terran_Marine,
            UnitType.Terran_Medic, UnitType.Terran_Medic};
        int[] targetIds = {101, 102, 103, 201, 202};
        int[] baseDistances = {20, 60, 90, 40, 150};
        TargetLedger ledger = TargetLedger.empty();
        Map<Integer, Integer> picks = new HashMap<>();

        for (int ling = 0; ling < 20; ling++) {
            List<TargetScorer.Candidate> candidates = new ArrayList<>();
            for (int t = 0; t < types.length; t++) {
                TargetScorer.Candidate base = new TargetScorer.Candidate(types[t], baseDistances[t] + ling, 1.0,
                        false, false);
                candidates.add(TargetScorer.withLedger(base, ling, UnitType.Zerg_Zergling, ZERGLING, targetIds[t],
                        () -> new Position(0, 0), ledger));
            }
            int chosen = TargetScorer.selectIndex(ZERGLING, candidates);
            ledger.record(ling, UnitType.Zerg_Zergling, targetIds[chosen]);
            picks.merge(chosen, 1, Integer::sum);
        }

        for (int t = 0; t < types.length; t++) {
            int cap = TargetScorer.meleeCap(types[t], UnitType.Zerg_Zergling);
            assertTrue(ledger.meleeAssigned(targetIds[t]) <= cap,
                    "target " + t + " holds " + ledger.meleeAssigned(targetIds[t]) + " over cap " + cap);
        }
        assertEquals(20, picks.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(picks.size() > 1);
        assertTrue(ledger.meleeAssigned(201) > 0, "the nearer Medic was never chosen");
    }

    private static final int NEAR_MARINE = 101;
    private static final int FAR_MARINE = 102;

    private static int lingPicksAMarine(TargetLedger ledger, int ling) {
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                TargetScorer.withLedger(at(UnitType.Terran_Marine, 20), ling, UnitType.Zerg_Zergling, ZERGLING,
                        NEAR_MARINE, () -> new Position(0, 0), ledger),
                TargetScorer.withLedger(at(UnitType.Terran_Marine, 200), ling, UnitType.Zerg_Zergling, ZERGLING,
                        FAR_MARINE, () -> new Position(0, 0), ledger));
        int targetId = TargetScorer.selectIndex(ZERGLING, candidates) == 0 ? NEAR_MARINE : FAR_MARINE;
        ledger.record(ling, UnitType.Zerg_Zergling, targetId);
        return targetId;
    }

    @Test
    void twoSquadsTargetingTheSameMarineRespectOneSharedCap() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetLedger frame = TargetLedger.empty();
        TargetLedger squadA = TargetLedger.empty();
        TargetLedger squadB = TargetLedger.empty();
        int perSquadOnNear = 0;

        for (int i = 0; i < cap; i++) {
            lingPicksAMarine(frame, 100 + i);
            lingPicksAMarine(frame, 200 + i);
            perSquadOnNear += lingPicksAMarine(squadA, 100 + i) == NEAR_MARINE ? 1 : 0;
            perSquadOnNear += lingPicksAMarine(squadB, 200 + i) == NEAR_MARINE ? 1 : 0;
        }

        assertEquals(cap, frame.meleeAssigned(NEAR_MARINE));
        assertEquals(cap, frame.meleeAssigned(FAR_MARINE));
        assertEquals(2 * cap, perSquadOnNear, "separate ledgers pile both squads onto the near Marine");
    }

    @Test
    void aLingRescoredOnTheSameFrameKeepsItsSlotAndANewcomerIsTurnedAway() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetLedger frame = TargetLedger.empty();
        for (int ling = 1; ling <= cap; ling++) {
            frame.record(ling, UnitType.Zerg_Zergling, NEAR_MARINE);
        }

        assertEquals(NEAR_MARINE, lingPicksAMarine(frame, 1));
        assertEquals(FAR_MARINE, lingPicksAMarine(frame, 50));
        assertEquals(cap, frame.meleeAssigned(NEAR_MARINE));
    }

    @Test
    void aLingWhoseOnlyNearbyMarineIsSaturatedFindsAnOpenMarineAmongTheWidenedCandidates() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetLedger frame = TargetLedger.empty();
        for (int other = 10; other < 10 + cap; other++) {
            frame.record(other, UnitType.Zerg_Zergling, NEAR_MARINE);
        }
        TargetScorer.Candidate nearby = TargetScorer.withLedger(at(UnitType.Terran_Marine, 30), 1,
                UnitType.Zerg_Zergling, ZERGLING, NEAR_MARINE, () -> new Position(0, 0), frame);
        TargetScorer.Candidate beyond = TargetScorer.withLedger(at(UnitType.Terran_Marine, 400), 1,
                UnitType.Zerg_Zergling, ZERGLING, FAR_MARINE, () -> new Position(0, 0), frame);

        List<TargetScorer.Candidate> inRadius = Collections.singletonList(nearby);
        assertTrue(inRadius.get(TargetScorer.selectIndex(ZERGLING, inRadius)).saturated());

        List<TargetScorer.Candidate> widened = Arrays.asList(nearby, beyond);
        int chosen = TargetScorer.selectIndex(ZERGLING, widened);
        assertEquals(1, chosen);
        assertFalse(widened.get(chosen).saturated());
    }

    @Test
    void aSeededOverloadIsShedDownToTheCapInOnePass() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetLedger frame = TargetLedger.empty();
        int lings = cap + 5;
        for (int ling = 1; ling <= lings; ling++) {
            frame.record(ling, UnitType.Zerg_Zergling, NEAR_MARINE);
        }

        for (int ling = 1; ling <= lings; ling++) {
            lingPicksAMarine(frame, ling);
        }

        assertEquals(cap, frame.meleeAssigned(NEAR_MARINE));
        assertEquals(5, frame.meleeAssigned(FAR_MARINE));
    }

    @Test
    void aSaturatedNearestMarineDoesNotHideANearerMedicFromTheNextLing() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Marine, 30, cap), lingOn(UnitType.Terran_Medic, 50, 0),
                lingOn(UnitType.Terran_Marine, 200, 0));

        assertEquals(1, TargetScorer.selectIndex(ZERGLING, candidates));
        assertEquals(TargetScorer.Reason.MEDIC_NEARER, TargetScorer.reasonAt(ZERGLING, candidates, 1));
    }

    @Test
    void whenEveryThreatIsSaturatedAMedicMustBeNearerThanTheNearestOne() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Marine, 30, cap), lingOn(UnitType.Terran_Medic, 50, 0));

        assertEquals(TargetScorer.Reason.UNARMED, TargetScorer.reasonAt(ZERGLING, candidates, 1));
    }

    @Test
    void aMutaliskAlsoPromotesAMedicNearerThanTheNearestMarine() {
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                at(UnitType.Terran_Marine, 120), at(UnitType.Terran_Medic, 40));

        assertEquals(1, TargetScorer.selectIndex(MUTALISK, candidates));
        assertEquals(TargetScorer.Reason.MEDIC_NEARER, TargetScorer.reasonAt(MUTALISK, candidates, 1));
    }

    @Test
    void theLedgerSetsTheLoadAndCapForAMeleeAttackerAndNoCapForARangedOne() {
        TargetLedger ledger = TargetLedger.empty();
        ledger.record(1, UnitType.Zerg_Zergling, 5);
        ledger.record(2, UnitType.Zerg_Zergling, 5);

        TargetScorer.Candidate forLing = TargetScorer.withLedger(at(UnitType.Terran_Marine, 50), 3,
                UnitType.Zerg_Zergling, ZERGLING, 5, () -> new Position(0, 0), ledger);
        TargetScorer.Candidate forHydra = TargetScorer.withLedger(at(UnitType.Terran_Marine, 50), 3,
                UnitType.Zerg_Hydralisk, false, 5, () -> new Position(0, 0), ledger);

        assertEquals(2, forLing.assignedMelee());
        assertEquals(TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling), forLing.meleeCap());
        assertEquals(Integer.MAX_VALUE, forHydra.meleeCap());
    }

    @Test
    void theLedgerMarksACoveredUnitOrDepotButNotTheDefenceStructureItself() {
        Position bunker = new Position(1000, 1000);
        TargetLedger ledger = new TargetLedger(
                Collections.singletonList(new StaticDefenseZone(UnitType.Terran_Bunker, bunker, 160)));
        Position near = new Position(1080, 1000);

        assertTrue(covered(ledger, UnitType.Terran_Marine, near));
        assertTrue(covered(ledger, UnitType.Terran_Supply_Depot, near));
        assertFalse(covered(ledger, UnitType.Terran_Bunker, bunker));
        assertFalse(covered(ledger, UnitType.Terran_Marine, new Position(1500, 1000)));
    }

    @Test
    void aFlyingAttackerOrAnEmptyLedgerNeverReadsTheCandidatePosition() {
        TargetLedger withBunker = new TargetLedger(Collections.singletonList(
                new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1000, 1000), 160)));
        Supplier<Position> unreadable = () -> {
            throw new AssertionError("position read");
        };

        TargetScorer.withLedger(at(UnitType.Terran_Marine, 50), 3, UnitType.Zerg_Mutalisk, MUTALISK, 1, unreadable,
                withBunker);
        TargetScorer.withLedger(at(UnitType.Terran_Marine, 50), 3, UnitType.Zerg_Zergling, ZERGLING, 1, unreadable,
                TargetLedger.empty());
    }

    private static boolean covered(TargetLedger ledger, UnitType type, Position position) {
        return TargetScorer.withLedger(at(type, 50), 3, UnitType.Zerg_Zergling, ZERGLING, 1, () -> position, ledger)
                .inGroundDefense();
    }

    @Test
    void aNearerMedicOutscoresAFartherMarine() {
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Marine, 120, 0), lingOn(UnitType.Terran_Medic, 40, 0));

        int chosen = TargetScorer.selectIndex(ZERGLING, candidates);

        assertEquals(1, chosen);
        assertEquals(TargetScorer.Reason.MEDIC_NEARER, TargetScorer.reasonAt(ZERGLING, candidates, chosen));
        assertEquals(TargetScorer.Priority.CRITICAL, TargetScorer.Reason.MEDIC_NEARER.priority());
    }

    @Test
    void aMedicNoNearerThanTheNearestThreatStaysNormal() {
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Marine, 40, 0), lingOn(UnitType.Terran_Medic, 40, 0));

        assertEquals(0, TargetScorer.selectIndex(ZERGLING, candidates));
        assertEquals(TargetScorer.Reason.UNARMED, TargetScorer.reasonAt(ZERGLING, candidates, 1));
    }

    @Test
    void aMedicWithNoThreatAmongTheCandidatesStaysNormal() {
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Supply_Depot, 100, 0), lingOn(UnitType.Terran_Medic, 40, 0));

        assertEquals(TargetScorer.Reason.UNARMED, TargetScorer.reasonAt(ZERGLING, candidates, 1));
    }

    @Test
    void aMedicHealingInjuredBioIsCriticalAndTakesTheSlotAFullMarineCannotOffer() {
        int marineCap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetScorer.Candidate healingMedic = lingOn(UnitType.Terran_Medic, 150, 0).withHealingInjuredBio(true);
        List<TargetScorer.Candidate> candidates = Arrays.asList(
                lingOn(UnitType.Terran_Marine, 50, marineCap), healingMedic);

        assertEquals(1, TargetScorer.selectIndex(ZERGLING, candidates));
        assertEquals(TargetScorer.Reason.MEDIC_HEALING, TargetScorer.reasonAt(ZERGLING, candidates, 1));
    }

    @Test
    void aTargetIsSaturatedExactlyAtItsCap() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);

        assertFalse(lingOn(UnitType.Terran_Marine, 50, cap - 1).saturated());
        assertTrue(lingOn(UnitType.Terran_Marine, 50, cap).saturated());
    }

    @Test
    void aSaturatedTargetLosesToAFartherUnsaturatedOneInTheSameTierEvenWhenItIsTheCurrentTarget() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        TargetScorer.Candidate currentFull = new TargetScorer.Candidate(UnitType.Terran_Marine, 10, 0.1, true, false)
                .withMeleeLoad(cap, TargetScorer.loadCap(UnitType.Zerg_Zergling, UnitType.Terran_Marine));

        assertEquals(1, TargetScorer.selectIndex(ZERGLING,
                Arrays.asList(currentFull, lingOn(UnitType.Terran_Marine, 250, 0))));
    }

    @Test
    void saturationNeverDropsATargetBelowALowerTier() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);

        assertEquals(0, TargetScorer.selectIndex(ZERGLING,
                Arrays.asList(lingOn(UnitType.Terran_Marine, 50, cap + 5), lingOn(UnitType.Terran_SCV, 10, 0))));
    }

    @Test
    void whenEveryTargetIsSaturatedTheNearestStillWins() {
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);

        assertEquals(1, TargetScorer.selectIndex(ZERGLING, Arrays.asList(
                lingOn(UnitType.Terran_Marine, 90, cap), lingOn(UnitType.Terran_Marine, 30, cap + 2))));
    }

    @Test
    void aRangedAttackerIsNeverSaturated() {
        TargetScorer.Candidate crowded = new TargetScorer.Candidate(UnitType.Terran_Marine, 50, 1.0, false, false)
                .withMeleeLoad(100, TargetScorer.loadCap(UnitType.Zerg_Hydralisk, UnitType.Terran_Marine));

        assertFalse(crowded.saturated());
    }

    @Test
    void onlyAttackersWithAGroundWeaponShorterThanATileAreMelee() {
        assertTrue(TargetScorer.isMelee(UnitType.Zerg_Zergling));
        assertTrue(TargetScorer.isMelee(UnitType.Zerg_Ultralisk));
        assertTrue(TargetScorer.isMelee(UnitType.Protoss_Zealot));
        assertFalse(TargetScorer.isMelee(UnitType.Zerg_Hydralisk));
        assertFalse(TargetScorer.isMelee(UnitType.Zerg_Mutalisk));
        assertFalse(TargetScorer.isMelee(UnitType.Terran_Medic));
        assertFalse(TargetScorer.isMelee(UnitType.Zerg_Lurker));
    }

    @Test
    void theMeleeCapGrowsWithTheTargetFootprint() {
        int marine = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        int bunker = TargetScorer.meleeCap(UnitType.Terran_Bunker, UnitType.Zerg_Zergling);

        assertEquals(8, marine);
        assertTrue(bunker > marine);
        assertTrue(TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Ultralisk) < marine);
    }

    @Test
    void aGroundAttackerPrefersATargetOutsideStaticDefenceUnlessItIsTwiceAsFar() {
        TargetScorer.Candidate covered = lingOn(UnitType.Terran_Marine, 50, 0).withGroundDefense(true);

        assertEquals(1, TargetScorer.selectIndex(ZERGLING,
                Arrays.asList(covered, lingOn(UnitType.Terran_Marine, 80, 0))));
        assertEquals(0, TargetScorer.selectIndex(ZERGLING,
                Arrays.asList(covered, lingOn(UnitType.Terran_Marine, 120, 0))));
    }

    @Test
    void aFlyingAttackerIgnoresTheGroundStaticDefencePenalty() {
        TargetScorer.Candidate covered = at(UnitType.Terran_Marine, 50).withGroundDefense(true);

        assertEquals(0, TargetScorer.selectIndex(MUTALISK,
                Arrays.asList(covered, at(UnitType.Terran_Marine, 80))));
    }

    @Test
    void aMedicSupportsOnlyInjuredNonMedicBioWithinItsSeekRange() {
        int seek = UnitType.Terran_Medic.seekRange();
        int injured = UnitType.Terran_Marine.maxHitPoints() - 1;

        assertTrue(TargetScorer.supportsInjuredBio(UnitType.Terran_Marine, injured, seek));
        assertFalse(TargetScorer.supportsInjuredBio(UnitType.Terran_Marine, injured, seek + 1));
        assertFalse(TargetScorer.supportsInjuredBio(UnitType.Terran_Marine,
                UnitType.Terran_Marine.maxHitPoints(), 10));
        assertFalse(TargetScorer.supportsInjuredBio(UnitType.Terran_Medic, 1, 10));
        assertFalse(TargetScorer.supportsInjuredBio(UnitType.Terran_SCV, 1, 10));
        assertFalse(TargetScorer.supportsInjuredBio(UnitType.Terran_Vulture, 1, 10));
    }
}
