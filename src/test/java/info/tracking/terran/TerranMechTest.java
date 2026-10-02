package info.tracking.terran;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranMechTest {

    @Test
    void aSiegeTankIsMechEvidence() {
        assertTrue(TerranMech.matches(1, 0, 0, 0, 0));
    }

    @Test
    void aMachineShopIsMechEvidence() {
        assertTrue(TerranMech.matches(0, 1, 0, 0, 0));
    }

    @Test
    void aSpiderMineIsMechEvidence() {
        assertTrue(TerranMech.matches(0, 0, 1, 0, 0));
    }

    @Test
    void aGoliathIsMechEvidence() {
        assertTrue(TerranMech.matches(0, 0, 0, 1, 0));
    }

    @Test
    void twoFactoriesAreMechEvidence() {
        assertTrue(TerranMech.matches(0, 0, 0, 0, TerranMech.MECH_FACTORIES));
    }

    @Test
    void oneBareFactoryIsNotMechEvidence() {
        assertFalse(TerranMech.matches(0, 0, 0, 0, 1));
    }

    @Test
    void nothingIsNotMechEvidence() {
        assertFalse(TerranMech.matches(0, 0, 0, 0, 0));
    }

    @Test
    void isATerranStrategyNamedForTheLearningFile() {
        TerranMech strategy = new TerranMech();

        assertEquals("TerranMech", strategy.getName());
        assertEquals(Race.Terran, strategy.getRace());
    }

    @Test
    void mechIsReadBackFromTheJoinedDetectedStrategies() {
        assertTrue(TerranMech.isMechIn("2RaxAcademy;TerranMech;EarlyRush"));
        assertTrue(TerranMech.isMechIn("TerranMech"));
    }

    @Test
    void mechIsNotReadFromAMissingOrUnrelatedList() {
        assertFalse(TerranMech.isMechIn(null));
        assertFalse(TerranMech.isMechIn(""));
        assertFalse(TerranMech.isMechIn("2RaxAcademy;SCVRush"));
        assertFalse(TerranMech.isMechIn("NotTerranMech"));
    }

    @Test
    void mechPersistsWhenTwoOfTheLastThreeGamesDetectedIt() {
        assertTrue(TerranMech.isPersistent(Arrays.asList("TerranMech", "", "TerranMech;EarlyRush")));
    }

    @Test
    void mechDoesNotPersistOnASingleRecentGame() {
        assertFalse(TerranMech.isPersistent(Arrays.asList("", "", "TerranMech")));
    }

    @Test
    void mechOlderThanTheRecentWindowIsIgnored() {
        assertFalse(TerranMech.isPersistent(Arrays.asList("TerranMech", "TerranMech", "", "", "")));
    }

    @Test
    void anEmptyHistoryDoesNotPersist() {
        assertFalse(TerranMech.isPersistent(Collections.emptyList()));
    }
}
