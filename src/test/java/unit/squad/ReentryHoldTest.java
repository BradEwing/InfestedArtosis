package unit.squad;

import bwapi.Position;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReentryHoldTest {

    private static final int LEFT = 9000;
    private static final double EXIT = 100;
    private static final double CURRENT = 40;
    private static final Position ANCHOR = new Position(1000, 1000);
    private static final Position ELSEWHERE = new Position(3000, 1000);

    private final boolean holdSetting = Config.airReentryHold;

    @AfterEach
    void restoreSwitch() {
        Config.airReentryHold = holdSetting;
    }

    private static HorizonCombatSimulator.DebugSnapshot read(double enemyStrength, Position centre, boolean measured) {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.setEnemyTotal(enemyStrength);
        snapshot.setSquadCenter(centre);
        snapshot.setEnemyMeasured(measured);
        return snapshot;
    }

    private static Squad leaving(Squad squad, int frame, double enemyStrength, Position centre) {
        squad.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(squad, read(enemyStrength, centre, true), frame);
        squad.setStatus(SquadStatus.RETREAT);
        return squad;
    }

    private static double held(Squad squad, int age) {
        return squad.reentryHeldStrength(LEFT + age, ANCHOR, CURRENT);
    }

    @Test
    void theExitStrengthIsHeldInFullThenDecaysLinearlyToTheCurrentStrengthByTheEnd() {
        Squad squad = leaving(new AirSquad(), LEFT, EXIT, ANCHOR);
        int full = Squad.REENTRY_HOLD_FULL_FRAMES;
        int end = Squad.REENTRY_HOLD_FRAMES;

        assertEquals(EXIT, held(squad, 0));
        assertEquals(EXIT, held(squad, full - 1));
        assertEquals(EXIT, held(squad, full));
        assertEquals((EXIT + CURRENT) / 2, held(squad, (full + end) / 2), 1e-9);
        assertEquals(CURRENT + (EXIT - CURRENT) / (end - full), held(squad, end - 1), 1e-9);
        assertEquals(CURRENT, held(squad, end));
        assertEquals(CURRENT, held(squad, end + 1000));
    }

    @Test
    void theHeldStrengthNeverLowersAReadThatMeasuresMoreThanTheSquadLeft() {
        Squad squad = leaving(new AirSquad(), LEFT, EXIT, ANCHOR);
        assertEquals(EXIT * 2, squad.reentryHeldStrength(LEFT, ANCHOR, EXIT * 2));
    }

    @Test
    void aReadTakenFarFromWhereTheSquadLeftIsJudgedFresh() {
        Squad squad = leaving(new AirSquad(), LEFT, EXIT, ANCHOR);
        int release = Squad.REENTRY_HOLD_RELEASE_DISTANCE;
        Position edge = new Position(ANCHOR.getX() + release, ANCHOR.getY());
        Position past = new Position(ANCHOR.getX() + release + 1, ANCHOR.getY());

        assertEquals(EXIT, squad.reentryHeldStrength(LEFT, edge, CURRENT));
        assertEquals(CURRENT, squad.reentryHeldStrength(LEFT, past, CURRENT));
        assertEquals(CURRENT, squad.reentryHeldStrength(LEFT, null, CURRENT));
    }

    @Test
    void anyStatusOtherThanRetreatEndsTheHold() {
        for (SquadStatus status : SquadStatus.values()) {
            Squad squad = leaving(new AirSquad(), LEFT, EXIT, ANCHOR);
            squad.setStatus(status);
            assertEquals(status == SquadStatus.RETREAT ? EXIT : CURRENT, held(squad, 0), status.name());
        }
    }

    @Test
    void onlyAnAirSquadLeavingFightOnAMeasuredReadWithTheSwitchOnOpensAHold() {
        assertEquals(EXIT, held(leaving(new AirSquad(), LEFT, EXIT, ANCHOR), 0));
        assertEquals(CURRENT, held(leaving(new GroundSquad(), LEFT, EXIT, ANCHOR), 0));

        Squad retreating = new AirSquad();
        retreating.setStatus(SquadStatus.RETREAT);
        SquadManager.noteFightExit(retreating, read(EXIT, ANCHOR, true), LEFT);
        assertEquals(CURRENT, held(retreating, 0));

        Squad unread = new AirSquad();
        unread.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(unread, null, LEFT);
        unread.setStatus(SquadStatus.RETREAT);
        assertEquals(CURRENT, held(unread, 0));

        Squad unmeasured = new AirSquad();
        unmeasured.setStatus(SquadStatus.FIGHT);
        SquadManager.noteFightExit(unmeasured, read(EXIT, ANCHOR, false), LEFT);
        unmeasured.setStatus(SquadStatus.RETREAT);
        assertEquals(CURRENT, held(unmeasured, 0));

        Config.airReentryHold = false;
        assertEquals(CURRENT, held(leaving(new AirSquad(), LEFT, EXIT, ANCHOR), 0));
    }

    @Test
    void aHarassExitOpensTheHoldOnTheReadTakenWhereTheSquadLeft() {
        Squad squad = new AirSquad();
        squad.setStatus(SquadStatus.HARASS);
        squad.setStatus(SquadStatus.RETREAT);
        SquadManager.openReentryHold(squad, read(EXIT, ANCHOR, true), LEFT);
        assertEquals(EXIT, held(squad, 0));
    }

    @Test
    void aLaterExitReplacesTheHeldStrengthAndAnchor() {
        Squad squad = leaving(new AirSquad(), LEFT, EXIT, ELSEWHERE);
        leaving(squad, LEFT + 100, EXIT / 2, ANCHOR);
        assertEquals(EXIT / 2, squad.reentryHeldStrength(LEFT + 100, ANCHOR, CURRENT / 2));
        assertEquals(CURRENT, squad.reentryHeldStrength(LEFT + 100, ELSEWHERE, CURRENT));
    }

    @Test
    void aMergeInRetreatKeepsTheHoldOfTheSourceThatLeftLastAndAnyOtherStatusDropsIt() {
        Squad earlier = leaving(new AirSquad(), LEFT, EXIT * 3, ELSEWHERE);
        Squad later = leaving(new AirSquad(), LEFT + 10, EXIT, ANCHOR);
        Squad untouched = new AirSquad();
        untouched.setStatus(SquadStatus.RETREAT);

        Squad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.asList(earlier, later, untouched));
        assertEquals(EXIT, merged.reentryHeldStrength(LEFT + 10, ANCHOR, CURRENT));
        assertEquals(CURRENT, merged.reentryHeldStrength(LEFT + 10, ELSEWHERE, CURRENT));

        Squad fighting = new AirSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        Squad mergedIntoFight = new AirSquad();
        mergedIntoFight.inheritStateFrom(Arrays.asList(later, fighting));
        mergedIntoFight.setStatus(SquadStatus.RETREAT);
        assertEquals(CURRENT, mergedIntoFight.reentryHeldStrength(LEFT + 10, ANCHOR, CURRENT));
    }
}
