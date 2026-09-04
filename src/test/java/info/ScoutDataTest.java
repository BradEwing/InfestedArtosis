package info;

import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutDataTest {

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
}
