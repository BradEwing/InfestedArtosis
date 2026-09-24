package strategy.buildorder.opener;

import bwapi.Race;
import info.TechProgression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NineHatchTest {

    private static final int NINE_DRONE_SUPPLY = 18;

    private static final int NINE_DRONES = 9;

    private static final int ONE_BASE = 1;

    private static final int TWO_BASES = 2;

    @Test
    void dronesUntilNine() {
        assertTrue(NineHatch.shouldPlanDrone(NINE_DRONES - 1, ONE_BASE));
        assertFalse(NineHatch.shouldPlanDrone(NINE_DRONES, ONE_BASE));
    }

    @Test
    void stopsDroningOnceTheNaturalIsCommitted() {
        assertFalse(NineHatch.shouldPlanDrone(NINE_DRONES - 1, TWO_BASES));
    }

    @Test
    void queuesTheHatcheryAtNineDrones() {
        assertTrue(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY, ONE_BASE));
        assertFalse(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY - 2, ONE_BASE));
    }

    @Test
    void queuesOnlyOneHatchery() {
        assertFalse(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY, TWO_BASES));
    }

    @Test
    void waitsForTheHatcheryBeforeTheReplacementDrone() {
        assertFalse(NineHatch.shouldPlanReplacementDrone(ONE_BASE, NINE_DRONES));
    }

    @Test
    void queuesExactlyOneReplacementDroneWhileTheBuilderWalks() {
        assertTrue(NineHatch.shouldPlanReplacementDrone(TWO_BASES, NINE_DRONES));
        assertFalse(NineHatch.shouldPlanReplacementDrone(TWO_BASES, NINE_DRONES + 1));
    }

    @Test
    void queuesExactlyOneReplacementDroneAfterTheMorph() {
        int eightDronesAndTheHatchery = NINE_DRONES - 1 + 1;
        assertTrue(NineHatch.shouldPlanReplacementDrone(TWO_BASES, eightDronesAndTheHatchery));
        assertFalse(NineHatch.shouldPlanReplacementDrone(TWO_BASES, eightDronesAndTheHatchery + 1));
    }

    @Test
    void queuesThePoolAfterTheReplacementDrone() {
        assertFalse(NineHatch.shouldPlanPool(NINE_DRONES, true));
        assertTrue(NineHatch.shouldPlanPool(NINE_DRONES + 1, true));
    }

    @Test
    void queuesThePoolOnlyOnce() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpawningPool(true);
        assertFalse(NineHatch.shouldPlanPool(NINE_DRONES + 1, techProgression.canPlanPool()));
    }

    /**
     * The pool predicate takes no hatchery term, so it holds with the natural walking, morphing,
     * completed, or cancelled after the replacement drone was made.
     */
    @Test
    void doesNotGateThePoolOnTheHatchery() {
        assertTrue(NineHatch.shouldPlanPool(NINE_DRONES + 1, new TechProgression().canPlanPool()));
        assertFalse(NineHatch.shouldPlanHatchery(NINE_DRONE_SUPPLY, TWO_BASES));
        assertFalse(NineHatch.shouldPlanReplacementDrone(TWO_BASES, NINE_DRONES + 1));
    }

    @Test
    void transitionsOnceThePoolIsCommitted() {
        assertFalse(NineHatch.openerComplete(0));
        assertTrue(NineHatch.openerComplete(1));
    }

    @Test
    void playsEveryKnownRace() {
        NineHatch opener = new NineHatch();
        assertTrue(opener.playsRace(Race.Protoss));
        assertTrue(opener.playsRace(Race.Terran));
        assertTrue(opener.playsRace(Race.Zerg));
        assertFalse(opener.playsRace(Race.Unknown));
        assertTrue(opener.isOpener());
    }
}
