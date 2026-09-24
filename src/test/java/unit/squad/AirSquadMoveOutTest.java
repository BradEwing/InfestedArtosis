package unit.squad;

import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirSquadMoveOutTest {

    private static final double AT_HOME = 70;
    private static final double FAR_FROM_HOME = 3903;

    private static Map<UnitType, Integer> composition(UnitType type, int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(type, count);
        return composition;
    }

    private static SquadManager.SquadAction launchFromRally(Map<UnitType, Integer> composition, Race opponent) {
        boolean scourgeOnly = composition.size() == 1 && composition.containsKey(UnitType.Zerg_Scourge);
        return SquadManager.chooseSquadAction(false, SquadManager.airMoveOutUnits(composition),
                SquadManager.airMoveOutThreshold(scourgeOnly, opponent), SquadStatus.RALLY, false, AT_HOME);
    }

    @Test
    void oneMutaliskDoesNotLaunchAgainstProtossOrTerran() {
        Map<UnitType, Integer> oneMutalisk = composition(UnitType.Zerg_Mutalisk, 1);

        assertEquals(SquadManager.SquadAction.RALLY, launchFromRally(oneMutalisk, Race.Protoss));
        assertEquals(SquadManager.SquadAction.RALLY, launchFromRally(oneMutalisk, Race.Terran));
    }

    @Test
    void theDefaultThresholdCountOfMutalisksLaunches() {
        assertEquals(5, SquadManager.AIR_MOVE_OUT_UNITS);
        Map<UnitType, Integer> fourMutalisks = composition(UnitType.Zerg_Mutalisk, SquadManager.AIR_MOVE_OUT_UNITS - 1);
        Map<UnitType, Integer> fiveMutalisks = composition(UnitType.Zerg_Mutalisk, SquadManager.AIR_MOVE_OUT_UNITS);

        assertEquals(SquadManager.SquadAction.RALLY, launchFromRally(fourMutalisks, Race.Protoss));
        assertEquals(SquadManager.SquadAction.LAUNCH, launchFromRally(fiveMutalisks, Race.Protoss));
        assertEquals(SquadManager.SquadAction.LAUNCH, launchFromRally(fiveMutalisks, Race.Terran));
        assertEquals(SquadManager.SquadAction.LAUNCH, launchFromRally(fiveMutalisks, Race.Unknown));
    }

    @Test
    void theVsZergThresholdIsAppliedByUnitCount() {
        int threshold = SquadManager.AIR_MOVE_OUT_UNITS_VS_ZERG;

        assertEquals(threshold, SquadManager.airMoveOutThreshold(false, Race.Zerg));
        assertEquals(SquadManager.SquadAction.RALLY,
                launchFromRally(composition(UnitType.Zerg_Mutalisk, threshold - 1), Race.Zerg));
        assertEquals(SquadManager.SquadAction.LAUNCH,
                launchFromRally(composition(UnitType.Zerg_Mutalisk, threshold), Race.Zerg));
    }

    @Test
    void theScourgeOnlyThresholdIsAppliedByUnitCountAgainstEveryRace() {
        int threshold = SquadManager.SCOURGE_MOVE_OUT_UNITS;

        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            assertEquals(threshold, SquadManager.airMoveOutThreshold(true, race));
            assertEquals(SquadManager.SquadAction.RALLY,
                    launchFromRally(composition(UnitType.Zerg_Scourge, threshold - 1), race));
            assertEquals(SquadManager.SquadAction.LAUNCH,
                    launchFromRally(composition(UnitType.Zerg_Scourge, threshold), race));
        }
    }

    @Test
    void airStrengthCountsUnitsNotSupply() {
        assertEquals(1, SquadManager.airMoveOutUnits(composition(UnitType.Zerg_Mutalisk, 1)));
        assertEquals(3, SquadManager.airMoveOutUnits(composition(UnitType.Zerg_Scourge, 3)));
    }

    @Test
    void escortingOverlordsDoNotCountTowardTheThreshold() {
        Map<UnitType, Integer> composition = composition(UnitType.Zerg_Mutalisk, 4);
        composition.put(UnitType.Zerg_Overlord, 2);

        assertEquals(4, SquadManager.airMoveOutUnits(composition));
        assertEquals(SquadManager.SquadAction.RALLY, launchFromRally(composition, Race.Protoss));
    }

    @Test
    void mixedAirCompositionsCountEveryCombatFlyer() {
        Map<UnitType, Integer> composition = composition(UnitType.Zerg_Mutalisk, 3);
        composition.put(UnitType.Zerg_Scourge, 1);
        composition.put(UnitType.Zerg_Guardian, 1);

        assertEquals(5, SquadManager.airMoveOutUnits(composition));
        assertEquals(SquadManager.SquadAction.LAUNCH, launchFromRally(composition, Race.Terran));
    }

    @Test
    void aCommittedAirSquadBelowTheThresholdIsNotRecalled() {
        int oneMutalisk = SquadManager.airMoveOutUnits(composition(UnitType.Zerg_Mutalisk, 1));
        int threshold = SquadManager.airMoveOutThreshold(false, Race.Protoss);

        assertEquals(SquadManager.SquadAction.SIMULATE,
                SquadManager.chooseSquadAction(false, oneMutalisk, threshold, SquadStatus.FIGHT, true, FAR_FROM_HOME));
        assertEquals(SquadManager.SquadAction.SIMULATE,
                SquadManager.chooseSquadAction(false, oneMutalisk, threshold, SquadStatus.RETREAT, true,
                        FAR_FROM_HOME));
    }

    @Test
    void aScourgeDoesNotJoinAMutaliskSquad() {
        Map<UnitType, Integer> mutalisks = composition(UnitType.Zerg_Mutalisk, 3);

        assertFalse(SquadManager.mayJoinAirSquad(UnitType.Zerg_Scourge, SquadManager.holdsOnlyScourge(mutalisks)));
    }

    @Test
    void aMutaliskDoesNotJoinAScourgeSquad() {
        Map<UnitType, Integer> scourge = composition(UnitType.Zerg_Scourge, 1);

        assertFalse(SquadManager.mayJoinAirSquad(UnitType.Zerg_Mutalisk, SquadManager.holdsOnlyScourge(scourge)));
        assertFalse(SquadManager.mayJoinAirSquad(UnitType.Zerg_Guardian, SquadManager.holdsOnlyScourge(scourge)));
    }

    @Test
    void likeAirUnitsStillJoinEachOther() {
        assertTrue(SquadManager.mayJoinAirSquad(UnitType.Zerg_Scourge,
                SquadManager.holdsOnlyScourge(composition(UnitType.Zerg_Scourge, 1))));
        assertTrue(SquadManager.mayJoinAirSquad(UnitType.Zerg_Mutalisk,
                SquadManager.holdsOnlyScourge(composition(UnitType.Zerg_Mutalisk, 1))));
    }

    @Test
    void aScourgeSquadDoesNotMergeWithAMutaliskSquad() {
        boolean scourge = SquadManager.holdsOnlyScourge(composition(UnitType.Zerg_Scourge, 2));
        boolean mutalisks = SquadManager.holdsOnlyScourge(composition(UnitType.Zerg_Mutalisk, 4));

        assertFalse(SquadManager.mayMergeAirSquads(scourge, mutalisks));
        assertFalse(SquadManager.mayMergeAirSquads(mutalisks, scourge));
        assertTrue(SquadManager.mayMergeAirSquads(scourge, scourge));
        assertTrue(SquadManager.mayMergeAirSquads(mutalisks, mutalisks));
    }

    @Test
    void anOverlordEscortDoesNotStopASquadBeingAScourgeSquad() {
        Map<UnitType, Integer> escorted = composition(UnitType.Zerg_Scourge, 2);
        escorted.put(UnitType.Zerg_Overlord, 1);
        Map<UnitType, Integer> mixed = composition(UnitType.Zerg_Scourge, 2);
        mixed.put(UnitType.Zerg_Mutalisk, 1);

        assertTrue(SquadManager.holdsOnlyScourge(escorted));
        assertFalse(SquadManager.holdsOnlyScourge(mixed));
        assertFalse(SquadManager.holdsOnlyScourge(composition(UnitType.Zerg_Overlord, 1)));
    }

    @Test
    void twoScourgeInTheirOwnSquadAreClearedToMoveOut() {
        Map<UnitType, Integer> pair = composition(UnitType.Zerg_Scourge, 2);

        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            assertEquals(SquadManager.SquadAction.LAUNCH, SquadManager.chooseSquadAction(false,
                    SquadManager.airMoveOutUnits(pair),
                    SquadManager.airMoveOutThreshold(SquadManager.holdsOnlyScourge(pair), race),
                    SquadStatus.RALLY, false, AT_HOME));
        }
    }

    @Test
    void aSplitOfAnAirSquadKeepsBothSidesAboveTheUnitThreshold() {
        int threshold = SquadManager.airMoveOutThreshold(false, Race.Protoss);

        assertFalse(SquadManager.splitKeepsBothSidesCommittable(6, 1, threshold));
        assertTrue(SquadManager.splitKeepsBothSidesCommittable(10, 5, threshold));
    }
}
