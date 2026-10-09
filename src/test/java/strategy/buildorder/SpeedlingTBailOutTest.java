package strategy.buildorder;

import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeedlingTBailOutTest {

    private static final Time EARLY = new Time(3, 0);

    @Test
    void aWallBailsOutBeforeTheDeadline() {
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL, SpeedlingT.bailOutTrigger(EARLY, true, false, false));
    }

    @Test
    void aMainBunkerBailsOutBeforeTheDeadline() {
        assertEquals(SpeedlingT.BailOutTrigger.BUNKER_MAIN, SpeedlingT.bailOutTrigger(EARLY, false, true, false));
    }

    @Test
    void aNaturalBunkerKeepsTheBuildEvenWithAWallOrMainBunker() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, true, false, true));
        assertNull(SpeedlingT.bailOutTrigger(EARLY, false, true, true));
        assertNull(SpeedlingT.bailOutTrigger(EARLY, true, true, true));
    }

    @Test
    void nothingDetectedKeepsTheBuild() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, false, false, false));
    }

    @Test
    void theDeadlineIsExclusive() {
        Time justBefore = new Time(SpeedlingT.BAIL_OUT_DEADLINE.getFrames() - 1);
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL, SpeedlingT.bailOutTrigger(justBefore, true, false, false));
        assertNull(SpeedlingT.bailOutTrigger(SpeedlingT.BAIL_OUT_DEADLINE, true, false, false));
        assertNull(SpeedlingT.bailOutTrigger(new Time(8, 0), false, true, false));
    }

    @Test
    void theDeadlineIsFourThirty() {
        assertEquals(new Time(4, 30), SpeedlingT.BAIL_OUT_DEADLINE);
    }

    @Test
    void theTransitionLabelNamesBothBuildsAndTheTrigger() {
        assertEquals("SpeedlingT>3HatchLurker:BUNKER_MAIN",
                SpeedlingT.label("3HatchLurker", SpeedlingT.BailOutTrigger.BUNKER_MAIN));
    }
}
