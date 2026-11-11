package unit.squad;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import unit.squad.SquadManager.LoneLaunch;
import util.Time;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoneLaunchTest {

    private static int frame(int minutes, int seconds) {
        return new Time(minutes, seconds).getFrames();
    }

    @Test
    void theMinimumMassMatchesTheMoveOutThresholdBeforeSixMinutesAndRampsAfter() {
        assertEquals(SquadManager.GROUND_MOVE_OUT_THRESHOLD, SquadManager.loneLaunchMinMass(0));
        assertEquals(SquadManager.GROUND_MOVE_OUT_THRESHOLD, SquadManager.loneLaunchMinMass(frame(5, 59)));
        assertEquals(SquadManager.LONE_LAUNCH_KNEE, SquadManager.loneLaunchMinMass(frame(6, 0)));
        assertEquals(SquadManager.LONE_LAUNCH_MID_GAME_SUPPLY, SquadManager.loneLaunchMinMass(frame(10, 0)));
        assertEquals(SquadManager.LONE_LAUNCH_LATE_GAME_SUPPLY, SquadManager.loneLaunchMinMass(frame(15, 0)));
    }

    @Test
    void aSquadBelowTheMinimumWithNoArmyToJoinStaysHome() {
        Position center = new Position(1000, 1000);
        int minMass = SquadManager.loneLaunchMinMass(frame(8, 0));

        assertNull(Standoff.reinforcementTarget(4, center, Collections.emptyList()));
        assertEquals(LoneLaunch.HELD, SquadManager.loneLaunch(true, 4, minMass, false));
    }

    @Test
    void earlyLingsAtTheMoveOutThresholdStillLaunch() {
        assertEquals(LoneLaunch.NONE, SquadManager.loneLaunch(true, SquadManager.GROUND_MOVE_OUT_THRESHOLD,
                SquadManager.loneLaunchMinMass(frame(3, 0)), false));
    }

    @Test
    void anExemptSquadLaunchesBelowTheMinimum() {
        assertEquals(LoneLaunch.EXEMPT, SquadManager.loneLaunch(true, 4, 8, true));
    }

    @Test
    void withTheSwitchOffABelowMinimumSquadLaunchesAsBefore() {
        assertEquals(LoneLaunch.BELOW_MIN_MASS, SquadManager.loneLaunch(false, 4, 8, false));
    }
}
