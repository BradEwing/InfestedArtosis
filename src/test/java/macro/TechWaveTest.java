package macro;

import bwapi.UnitType;
import bwapi.UpgradeType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.UnitPlan;
import macro.plan.UpgradePlan;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechWaveTest {

    private static final int FRAME = 7000;

    private static final int PLENTY = 5000;

    private static final int LARVA_TO_SPARE = TechWave.WAVE_SIZE + 1;

    private TechWave spireWave(int framesToCompletion) {
        return TechWave.pending(UnitType.Zerg_Spire, framesToCompletion, false, false, true);
    }

    private TechWave holdingSpireWave() {
        return spireWave(TechWave.WINDOW_FRAMES);
    }

    private Plan unit(UnitType unitType) {
        return new UnitPlan(unitType, FRAME);
    }

    @Test
    void aSpireInsideTheWindowHoldsProduction() {
        TechWave wave = holdingSpireWave();

        assertNotNull(wave);
        assertEquals(UnitType.Zerg_Mutalisk, wave.getUnit());
        assertTrue(wave.holdsProduction());
    }

    @Test
    void aLarvaMorphThatWouldBreachTheLarvaReserveIsHeld() {
        TechWave wave = holdingSpireWave();

        assertEquals(PlanBlocker.TECH_WAVE_RESERVE,
                wave.blocker(unit(UnitType.Zerg_Drone), TechWave.WAVE_SIZE, PLENTY, PLENTY));
        assertEquals(PlanBlocker.TECH_WAVE_RESERVE,
                wave.blocker(unit(UnitType.Zerg_Zergling), TechWave.WAVE_SIZE, PLENTY, PLENTY));
    }

    @Test
    void aLarvaMorphAboveTheLarvaReserveIsNotHeld() {
        TechWave wave = holdingSpireWave();

        assertEquals(PlanBlocker.NONE, wave.blocker(unit(UnitType.Zerg_Drone), LARVA_TO_SPARE, PLENTY, PLENTY));
    }

    @Test
    void theWaveReservesOneLarvaPerEggOfTheWave() {
        assertEquals(TechWave.WAVE_SIZE, holdingSpireWave().larvaReserve());
    }

    @Test
    void theOverlordTheWaveUnitAndEmergencyDefenceAreNeverHeld() {
        TechWave wave = holdingSpireWave();
        Plan emergencyLing = new UnitPlan(UnitType.Zerg_Zergling, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);
        Plan emergencyColony = new BuildingPlan(UnitType.Zerg_Creep_Colony, BuildOrder.EMERGENCY_DEFENSE_PRIORITY);

        assertEquals(PlanBlocker.NONE, wave.blocker(unit(UnitType.Zerg_Overlord), 0, 0, 0));
        assertEquals(PlanBlocker.NONE, wave.blocker(unit(UnitType.Zerg_Mutalisk), 0, 0, 0));
        assertEquals(PlanBlocker.NONE, wave.blocker(emergencyLing, 0, 0, 0));
        assertEquals(PlanBlocker.NONE, wave.blocker(emergencyColony, 0, 0, 0));
    }

    @Test
    void aBuildingThatWouldSpendTheWaveBankIsHeld() {
        TechWave wave = holdingSpireWave();
        Plan extractor = new BuildingPlan(UnitType.Zerg_Extractor, FRAME);

        assertEquals(PlanBlocker.TECH_WAVE_RESERVE,
                wave.blocker(extractor, 0, wave.mineralBank(), PLENTY));
        assertEquals(PlanBlocker.NONE,
                wave.blocker(extractor, 0, wave.mineralBank() + extractor.mineralPrice(), 0));
    }

    @Test
    void anUpgradeThatWouldSpendTheWaveGasIsHeld() {
        TechWave wave = holdingSpireWave();
        Plan carapace = new UpgradePlan(UpgradeType.Zerg_Flyer_Carapace, FRAME);

        assertEquals(PlanBlocker.TECH_WAVE_RESERVE,
                wave.blocker(carapace, 0, PLENTY, wave.gasBank() + carapace.gasPrice() - 1));
        assertEquals(PlanBlocker.NONE,
                wave.blocker(carapace, 0, PLENTY, wave.gasBank() + carapace.gasPrice()));
    }

    @Test
    void theBankIsTheWaveSizeTimesTheWaveUnitsCost() {
        TechWave wave = holdingSpireWave();

        assertEquals(TechWave.WAVE_SIZE * UnitType.Zerg_Mutalisk.mineralPrice(), wave.mineralBank());
        assertEquals(TechWave.WAVE_SIZE * UnitType.Zerg_Mutalisk.gasPrice(), wave.gasBank());
    }

    @Test
    void beforeTheWindowTheWavePlansSupplyButHoldsNothing() {
        TechWave wave = spireWave(TechWave.WINDOW_FRAMES + 1);

        assertNotNull(wave);
        assertFalse(wave.holdsProduction());
        assertEquals(PlanBlocker.NONE, wave.blocker(unit(UnitType.Zerg_Drone), 0, 0, 0));
    }

    @Test
    void supplyIsPlannedOneOverlordBuildTimeAheadOfTheWindow() {
        int horizon = TechWave.WINDOW_FRAMES + UnitType.Zerg_Overlord.buildTime();

        assertNotNull(spireWave(horizon));
        assertNull(spireWave(horizon + 1));
    }

    @Test
    void anOverlordIsNeededWhenTheWavesSupplyIsNotStandingOrInFlight() {
        TechWave wave = holdingSpireWave();
        int overlordSupply = UnitType.Zerg_Overlord.supplyProvided();

        assertTrue(wave.needsOverlord(wave.supplyNeeded() - 1, 0));
        assertFalse(wave.needsOverlord(wave.supplyNeeded(), 0));
        assertFalse(wave.needsOverlord(0, overlordSupply));
    }

    @Test
    void theWavesSupplyCoversEveryEgg() {
        assertEquals(TechWave.WAVE_SIZE * SupplyCapacity.morphSupplyCost(UnitType.Zerg_Mutalisk),
                holdingSpireWave().supplyNeeded());
    }

    @Test
    void noWaveIsPendingWhileARushReactionIsActive() {
        assertNull(TechWave.pending(UnitType.Zerg_Spire, TechWave.WINDOW_FRAMES, false, true, true));
    }

    @Test
    void noWaveIsPendingWithoutGroundSafety() {
        assertNull(TechWave.pending(UnitType.Zerg_Spire, TechWave.WINDOW_FRAMES, false, false, false));
    }

    @Test
    void noWaveIsPendingOnceTheWaveUnitIsAlreadyUnlocked() {
        assertNull(TechWave.pending(UnitType.Zerg_Spire, TechWave.WINDOW_FRAMES, true, false, true));
    }

    @Test
    void aStructureWithoutAWaveHoldsNothing() {
        assertNull(TechWave.waveUnit(UnitType.Zerg_Hydralisk_Den));
        assertNull(TechWave.pending(UnitType.Zerg_Hydralisk_Den, 0, false, false, true));
        assertNull(TechWave.pending(UnitType.Zerg_Evolution_Chamber, 0, false, false, true));
    }

    @Test
    void aZerglingLeadOrGroundUnitsAtOurBasesAreUnsafe() {
        assertTrue(TechWave.isGroundSafe(24, 7, 0));
        assertFalse(TechWave.isGroundSafe(4, 4 + TechWave.ZERGLING_THREAT_MARGIN, 0));
        assertTrue(TechWave.isGroundSafe(4, 4 + TechWave.ZERGLING_THREAT_MARGIN - 1, 0));
        assertFalse(TechWave.isGroundSafe(24, 0, 1));
    }

    @Test
    void aWaveMorphedFromAnExistingUnitHoldsNoLarva() {
        assertEquals(0, new TechWave(UnitType.Zerg_Lurker, 0).larvaReserve());
        assertEquals(0, new TechWave(UnitType.Zerg_Guardian, 0).larvaReserve());
    }

    @Test
    void aHeldPlanClaimsNeitherTheBankNorTheLarva() {
        assertFalse(ProductionManager.claimsBank(PlanBlocker.TECH_WAVE_RESERVE));
        assertFalse(ProductionManager.claimsLarva(unit(UnitType.Zerg_Drone), PlanBlocker.TECH_WAVE_RESERVE));
    }
}
