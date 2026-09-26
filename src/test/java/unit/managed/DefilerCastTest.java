package unit.managed;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.DarkSwarm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefilerCastTest {

    private static final int HALF_WIDTH = UnitType.Spell_Dark_Swarm.dimensionLeft();
    private static final Position FRONT = new Position(1000, 3300);

    @Test
    void aDistantTargetPutsTheFootprintsNearEdgeOnOurFront() {
        Position target = new Position(1300, 3300);

        Position cast = Defiler.castPoint(FRONT, target);

        assertEquals(new Position(1000 + HALF_WIDTH, 3300), cast);
        assertEquals(0, new DarkSwarm(0, cast, 900).gap(FRONT), 1e-9);
    }

    @Test
    void theCastIsAimedAlongTheLineToTheTarget() {
        Position target = new Position(1300, 3700);

        Position cast = Defiler.castPoint(FRONT, target);

        assertEquals(1048, cast.getX());
        assertEquals(3364, cast.getY());
    }

    @Test
    void aTargetWithinHalfTheFootprintIsCastOnDirectly() {
        Position target = new Position(1040, 3330);

        assertEquals(target, Defiler.castPoint(FRONT, target));
        assertEquals(FRONT, Defiler.castPoint(FRONT, FRONT));
    }

    @Test
    void buildingsThatCannotHitGroundUnitsAreLeftOutOfTheAim() {
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Supply_Depot));
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Barracks));
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Missile_Turret));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Bunker));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Goliath));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Siege_Tank_Siege_Mode));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Medic));
    }

    @Test
    void aLiveSwarmOverThePointRefusesTheCast() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertTrue(Defiler.blocksCast(existing, false, new Position(1232, 3520)));
        assertTrue(Defiler.blocksCast(existing, true, new Position(1300, 3590)));
    }

    @Test
    void aPointOutsideEveryFootprintIsCastOn() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertFalse(Defiler.blocksCast(existing, false, new Position(1232 + 100, 3520)));
    }

    @Test
    void aLapsingSwarmOverCommittedMeleeIsRecast() {
        int recast = Defiler.RECAST_REMAINING_FRAMES;
        Position point = new Position(1232, 3520);

        assertFalse(Defiler.blocksCast(new DarkSwarm(382, point, recast - 1), true, point));
        assertTrue(Defiler.blocksCast(new DarkSwarm(382, point, recast), true, point));
        assertTrue(Defiler.blocksCast(new DarkSwarm(382, point, recast - 1), false, point));
    }
}
