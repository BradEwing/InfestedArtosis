package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizonCombatSimulatorTest {

    private static final Position COLONY = new Position(1000, 1000);
    private static final int SUNKEN_RANGE = UnitType.Zerg_Sunken_Colony.groundWeapon().maxRange();
    private static final int SPORE_RANGE = UnitType.Zerg_Spore_Colony.airWeapon().maxRange();

    private static List<Position> at(int offsetX) {
        return Collections.singletonList(new Position(COLONY.getX() + offsetX, COLONY.getY()));
    }

    private static List<Position> none() {
        return Collections.emptyList();
    }

    @Test
    void sunkenCoversGroundThreatInRange() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(SUNKEN_RANGE), none()));
    }

    @Test
    void sunkenDoesNotCoverAirThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(0)));
    }

    @Test
    void sunkenDoesNotCoverAirThreatWhileCoveringGroundIsImpossible() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(SUNKEN_RANGE)));
    }

    @Test
    void sunkenDoesNotCoverDistantGroundThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(SUNKEN_RANGE + 512), none()));
    }

    @Test
    void sporeCoversAirThreatInRange() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, none(), at(SPORE_RANGE)));
    }

    @Test
    void sporeDoesNotCoverGroundThreat() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, at(0), none()));
    }

    @Test
    void creepColonyCoversNothing() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Creep_Colony, COLONY, at(0), at(0)));
    }

    @Test
    void noThreatsMeansNoCoverage() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), none()));
    }

    @Test
    void mixedThreatsCoverOnlyViaTheMatchingWeapon() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, at(0), at(0)));
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, at(0), at(0)));
    }

    @Test
    void withholdingGroundThreatsLeavesSunkenWithNothingToCover() {
        assertFalse(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Sunken_Colony, COLONY, none(), at(0)));
    }

    @Test
    void sporeStillCoversAirWhenGroundThreatsAreWithheld() {
        assertTrue(HorizonCombatSimulator.coversThreat(
                UnitType.Zerg_Spore_Colony, COLONY, none(), at(0)));
    }

    @Test
    void colonyWeaponRangesMatchTheGameData() {
        assertTrue(UnitType.Zerg_Sunken_Colony.airWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Spore_Colony.groundWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Creep_Colony.groundWeapon() == bwapi.WeaponType.None);
        assertTrue(UnitType.Zerg_Creep_Colony.airWeapon() == bwapi.WeaponType.None);
    }

    @Test
    void nonCombatFlyersAreNotTreatedAsAttackers() {
        assertFalse(UnitType.Zerg_Overlord.canAttack());
        assertFalse(UnitType.Protoss_Observer.canAttack());
        assertFalse(UnitType.Protoss_Shuttle.canAttack());
        assertFalse(UnitType.Terran_Dropship.canAttack());
        assertTrue(UnitType.Protoss_Carrier.canAttack());
        assertTrue(UnitType.Zerg_Mutalisk.canAttack());
    }
}
