package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.AirHarassEvaluator;
import unit.squad.CombatSimulator.CombatResult;
import unit.squad.Squad;
import unit.squad.SquadStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class AirReentryHoldTest {

    private static final UnitType MARINE = UnitType.Terran_Marine;
    private static final UnitType TURRET = UnitType.Terran_Missile_Turret;
    private static final double THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);
    private static final int FRAME = AirBattle.FRAME;
    private static final Position CENTRE = AirBattle.CENTRE;
    private static final int FLOCK = 4;
    private static final int REINFORCED = 12;
    private static final int MARINE_DISTANCE = 200;
    private static final int PULLED_BACK = 400;
    private static final double BACKED_OFF = 0.4;

    private final boolean holdSetting = Config.airReentryHold;
    private final boolean edgeSetting = Config.airTurretEdgeOfFire;

    @AfterEach
    void restoreSwitches() {
        Config.airReentryHold = holdSetting;
        Config.airTurretEdgeOfFire = edgeSetting;
    }

    private static void switches(boolean hold, boolean edge) {
        Config.airReentryHold = hold;
        Config.airTurretEdgeOfFire = edge;
    }

    private static AirBattle marines(int mutalisks) {
        return new AirBattle(mutalisks).enemy(MARINE, MARINE_DISTANCE, 0).enemy(MARINE, MARINE_DISTANCE, 16)
                .enemy(MARINE, MARINE_DISTANCE, 32);
    }

    private static AirBattle held(AirBattle battle, int age, double exitStrength, Position anchor) {
        battle.squad.setStatus(SquadStatus.RETREAT);
        battle.squad.startReentryHold(FRAME - age, exitStrength, anchor);
        return battle;
    }

    private static HorizonCombatSimulator.DebugSnapshot fresh(AirBattle battle) {
        assertEquals(ENGAGE, battle.evaluate());
        return battle.snapshot();
    }

    private static double exitStrength() {
        return fresh(marines(FLOCK)).getEnemyTotal() / BACKED_OFF;
    }

    @Test
    void aSquadReadingEngageAgainstFourTenthsOfTheStrengthItLeftStaysInRetreat() {
        for (boolean edge : new boolean[] {true, false}) {
            switches(true, edge);
            HorizonCombatSimulator.DebugSnapshot backedOff = fresh(marines(FLOCK));
            double exit = backedOff.getEnemyTotal() / BACKED_OFF;
            assertTrue(backedOff.getOverallRatio() * BACKED_OFF < THRESHOLD);

            AirBattle battle = held(marines(FLOCK), 10, exit, CENTRE);
            assertEquals(RETREAT, battle.evaluate());
            HorizonCombatSimulator.DebugSnapshot snapshot = battle.snapshot();
            assertEquals(exit, snapshot.getReentryHoldStrength(), 1e-9);
            assertEquals(backedOff.getEnemyTotal(), snapshot.getEnemyTotal(), 1e-9);
            assertEquals(backedOff.getOverallRatio(), snapshot.getOverallRatio(), 1e-9);
            assertEquals(FRAME, snapshot.getReentryHoldSinceFrame());
        }
    }

    @Test
    void reinforcementsThatClearTheThresholdAgainstTheStrengthItLeftGoBackIn() {
        switches(true, true);
        double exit = exitStrength();
        HorizonCombatSimulator.DebugSnapshot reinforced = fresh(marines(REINFORCED));
        assertTrue(reinforced.getOverallRatio() * reinforced.getEnemyTotal() / exit >= THRESHOLD);

        AirBattle battle = held(marines(REINFORCED), 10, exit, CENTRE);
        assertEquals(ENGAGE, battle.evaluate());
        assertEquals(exit, battle.snapshot().getReentryHoldStrength(), 1e-9);
        assertEquals(-1, battle.snapshot().getReentryHoldSinceFrame());
    }

    @Test
    void theHoldKeepsTheSquadOutWhileFullAndLetsItBackInOnceDecayed() {
        switches(true, true);
        double exit = exitStrength();
        assertEquals(RETREAT, held(marines(FLOCK), Squad.REENTRY_HOLD_FULL_FRAMES, exit, CENTRE).evaluate());
        AirBattle ended = held(marines(FLOCK), Squad.REENTRY_HOLD_END_FRAMES, exit, CENTRE);
        assertEquals(ENGAGE, ended.evaluate());
        assertEquals(-1, ended.snapshot().getReentryHoldStrength());
    }

    private static Position behind(double distance) {
        return new Position(CENTRE.getX() - (int) Math.round(distance), CENTRE.getY());
    }

    @Test
    void aReadAtTheEndDistanceFromWhereTheSquadLeftIsJudgedFresh() {
        switches(true, true);
        double exit = exitStrength();
        assertEquals(RETREAT, held(marines(FLOCK), 10, exit, behind(Squad.REENTRY_HOLD_FULL_DISTANCE)).evaluate());
        AirBattle far = held(marines(FLOCK), 10, exit, behind(Squad.REENTRY_HOLD_END_DISTANCE));
        assertEquals(ENGAGE, far.evaluate());
        assertEquals(-1, far.snapshot().getReentryHoldStrength());
    }

    @Test
    void aFlockThatRetreatedStraightAwayAtMutaliskSpeedForSeventyFramesIsStillHeld() {
        switches(true, true);
        double exit = exitStrength();
        int age = 70;
        AirBattle battle = held(marines(FLOCK), age, exit, behind(UnitType.Zerg_Mutalisk.topSpeed() * age));
        assertEquals(RETREAT, battle.evaluate());
        assertEquals(exit, battle.snapshot().getReentryHoldStrength(), 1e-9);
    }

    @Test
    void onlyARetreatingSquadIsHeld() {
        switches(true, true);
        double exit = exitStrength();
        for (SquadStatus status : SquadStatus.values()) {
            AirBattle battle = marines(FLOCK);
            battle.squad.setStatus(status);
            battle.squad.startReentryHold(FRAME - 10, exit, CENTRE);
            assertEquals(status == SquadStatus.RETREAT ? RETREAT : ENGAGE, battle.evaluate(), status.name());
        }
    }

    @Test
    void anAdvanceThatMeasuresNothingWhereTheSquadLeftAnEnemyIsHeld() {
        switches(true, true);
        double exit = exitStrength();
        assertEquals(ADVANCE, new AirBattle(FLOCK).evaluate());
        assertEquals(RETREAT, held(new AirBattle(FLOCK), 10, exit, CENTRE).evaluate());
    }

    @Test
    void aHarassExitEngageDoesNotBreakTheExitLockOnAReadTheHoldRefuses() {
        switches(true, true);
        double exit = exitStrength();
        AirBattle open = marines(FLOCK);
        open.squad.setStatus(SquadStatus.RETREAT);
        assertTrue(AirHarassEvaluator.breaksExitLock(true, open.evaluate(), open.snapshot().isEnemyMeasured()));

        AirBattle battle = held(marines(FLOCK), 10, exit, CENTRE);
        CombatResult result = battle.evaluate();
        assertFalse(AirHarassEvaluator.breaksExitLock(true, result, battle.snapshot().isEnemyMeasured()));
    }

    @Test
    void withTheHoldOffAHeldSquadReadsExactlyAsOneWithoutAHold() {
        double exit = exitStrength();
        for (boolean edge : new boolean[] {true, false}) {
            switches(false, edge);
            for (boolean turret : new boolean[] {true, false}) {
                AirBattle plain = marines(FLOCK);
                AirBattle battle = held(marines(FLOCK), 10, exit, CENTRE);
                if (turret) {
                    plain.enemy(TURRET, PULLED_BACK, 0);
                    battle.enemy(TURRET, PULLED_BACK, 0);
                }
                plain.squad.setStatus(SquadStatus.RETREAT);
                assertEquals(plain.evaluate(), battle.evaluate());
                assertEquals(plain.snapshot().getOverallRatio(), battle.snapshot().getOverallRatio(), 1e-9);
                assertEquals(plain.snapshot().getEnemyTotal(), battle.snapshot().getEnemyTotal(), 1e-9);
                assertEquals(-1, battle.snapshot().getReentryHoldStrength());
                assertEquals(-1, battle.snapshot().getReentryHoldSinceFrame());
            }
        }
    }

    @Test
    void withNoHoldOpenTheVerdictIsTheRawOne() {
        for (CombatResult raw : CombatResult.values()) {
            assertEquals(raw, HorizonCombatSimulator.reenter(raw, 10, 0, THRESHOLD));
        }
        assertEquals(ENGAGE, HorizonCombatSimulator.reenter(ENGAGE, 10 * THRESHOLD, 10, THRESHOLD));
        assertEquals(RETREAT, HorizonCombatSimulator.reenter(ENGAGE, 10 * THRESHOLD - 1, 10, THRESHOLD));
        assertEquals(RETREAT, HorizonCombatSimulator.reenter(RETREAT, 1000, 10, THRESHOLD));
    }
}
