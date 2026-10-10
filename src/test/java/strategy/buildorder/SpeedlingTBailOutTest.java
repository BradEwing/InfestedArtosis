package strategy.buildorder;

import bwapi.Race;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import info.tracking.terran.BunkerMain;
import info.tracking.terran.BunkerNatural;
import info.tracking.terran.TerranBaseStrategy;
import info.tracking.terran.TerranWallMain;
import info.tracking.terran.TerranWallNatural;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpeedlingTBailOutTest {

    private static final Time EARLY = new Time(3, 0);

    @Test
    void aWallBailsOutBeforeTheDeadline() {
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL, SpeedlingT.bailOutTrigger(EARLY, true, false));
    }

    @Test
    void aNaturalBunkerKeepsTheBuildEvenWithAWall() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, true, true));
    }

    @Test
    void nothingDetectedKeepsTheBuild() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, false, false));
    }

    @Test
    void theDeadlineIsExclusive() {
        Time justBefore = new Time(SpeedlingT.BAIL_OUT_DEADLINE.getFrames() - 1);
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL, SpeedlingT.bailOutTrigger(justBefore, true, false));
        assertNull(SpeedlingT.bailOutTrigger(SpeedlingT.BAIL_OUT_DEADLINE, true, false));
    }

    @Test
    void theDeadlineIsFourThirty() {
        assertEquals(new Time(4, 30), SpeedlingT.BAIL_OUT_DEADLINE);
    }

    @Test
    void theTransitionLabelNamesBothBuildsAndTheTrigger() {
        assertEquals("SpeedlingT>3HatchLurker:TERRAN_WALL",
                SpeedlingT.label("3HatchLurker", SpeedlingT.BailOutTrigger.TERRAN_WALL));
    }

    private static StrategyTracker tracker(TerranBaseStrategy... detected) {
        StrategyTracker tracker = new StrategyTracker(null, Race.Terran, new ObservedUnitTracker(), null, null, null,
                null);
        for (TerranBaseStrategy strategy : detected) {
            tracker.getDetectedStrategies().add(strategy);
        }
        return tracker;
    }

    @Test
    void aDetectedNaturalWallFiresTheBailOut() {
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL,
                SpeedlingT.bailOutTrigger(EARLY, Race.Terran, tracker(new TerranWallNatural())));
    }

    @Test
    void aDetectedMainWallFiresTheBailOut() {
        assertEquals(SpeedlingT.BailOutTrigger.TERRAN_WALL,
                SpeedlingT.bailOutTrigger(EARLY, Race.Terran, tracker(new TerranWallMain())));
    }

    @Test
    void aMainBunkerAloneDoesNotFireTheBailOut() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, Race.Terran, tracker(new BunkerMain())));
        assertNull(SpeedlingT.bailOutTrigger(new Time(0, 30), Race.Terran, tracker(new BunkerMain())));
    }

    @Test
    void aWallWithANaturalBunkerDoesNotFireTheBailOut() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, Race.Terran, tracker(new TerranWallNatural(), new BunkerNatural())));
    }

    @Test
    void noTrackerOrANonTerranRaceNeverFiresTheBailOut() {
        assertNull(SpeedlingT.bailOutTrigger(EARLY, Race.Terran, null));
        assertNull(SpeedlingT.bailOutTrigger(EARLY, Race.Protoss, tracker(new TerranWallNatural())));
    }
}
