package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretReentryBandTest {

    private static final int LEFT = 9000;
    private static final Position TURRET = new Position(640, 480);
    private static final Position OTHER_TURRET = new Position(960, 480);

    private static HorizonCombatSimulator.DebugSnapshot sampling(Position turret) {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.getAirEdgeOfFire().put(turret, 224);
        return snapshot;
    }

    private static Squad leaving(Squad squad) {
        squad.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(squad, sampling(TURRET), LEFT);
        squad.setStatus(SquadStatus.RETREAT);
        return squad;
    }

    @Test
    void anAirSquadLeavingFightHoldsTheTurretsItsReadSampledForTheBand() {
        Squad squad = leaving(new AirSquad());
        assertEquals(Collections.singleton(TURRET), squad.turretReentryBand(LEFT));
        assertEquals(Collections.singleton(TURRET), squad.turretReentryBand(LEFT + Squad.TURRET_REENTRY_BAND_FRAMES - 1));
        assertTrue(squad.turretReentryBand(LEFT + Squad.TURRET_REENTRY_BAND_FRAMES).isEmpty());
    }

    @Test
    void onlyAnAirSquadLeavingFightWithASnapshotOpensABand() {
        assertTrue(leaving(new GroundSquad()).turretReentryBand(LEFT).isEmpty());

        Squad retreating = new AirSquad();
        retreating.setStatus(SquadStatus.RETREAT);
        SquadManager.noteFightExit(retreating, sampling(TURRET), LEFT);
        assertTrue(retreating.turretReentryBand(LEFT).isEmpty());

        Squad unread = new AirSquad();
        unread.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(unread, null, LEFT);
        assertTrue(unread.turretReentryBand(LEFT).isEmpty());
    }

    @Test
    void anotherExitReplacesTheHeldTurrets() {
        Squad squad = leaving(new AirSquad());
        squad.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(squad, sampling(OTHER_TURRET), LEFT + 100);
        squad.setStatus(SquadStatus.RETREAT);
        assertEquals(Collections.singleton(OTHER_TURRET), squad.turretReentryBand(LEFT + 100));
    }

    @Test
    void aMergeInRetreatHoldsEverySourcesTurretsAndAnyOtherStatusDropsThem() {
        Squad first = leaving(new AirSquad());
        Squad second = new AirSquad();
        second.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(second, sampling(OTHER_TURRET), LEFT + 100);
        second.setStatus(SquadStatus.RETREAT);

        Squad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.asList(first, second));
        assertEquals(2, merged.turretReentryBand(LEFT + 100).size());
        assertEquals(2, merged.turretReentryBand(LEFT + Squad.TURRET_REENTRY_BAND_FRAMES).size());

        Squad fighting = new AirSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        Squad mergedIntoFight = new AirSquad();
        mergedIntoFight.inheritStateFrom(Arrays.asList(first, fighting));
        assertTrue(mergedIntoFight.turretReentryBand(LEFT).isEmpty());
    }
}
