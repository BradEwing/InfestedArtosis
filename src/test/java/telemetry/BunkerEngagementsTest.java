package telemetry;

import bwapi.Position;
import org.junit.jupiter.api.Test;
import telemetry.BunkerEngagements.BunkerSample;
import telemetry.BunkerEngagements.Closed;
import telemetry.BunkerEngagements.OurUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerEngagementsTest {

    private static final Position BUNKER_AT = new Position(1000, 1000);
    private static final Position NEAR = new Position(900, 1000);
    private static final Position FAR = new Position(3000, 3000);
    private static final int BUNKER_ID = 7;

    private static List<BunkerSample> bunker(int hitPoints) {
        return Collections.singletonList(new BunkerSample(BUNKER_ID, BUNKER_AT, hitPoints));
    }

    private static List<OurUnit> units(int count, Position at) {
        List<OurUnit> units = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            units.add(new OurUnit(at));
        }
        return units;
    }

    @Test
    void fewerThanTheMinimumUnitsOpenNoEngagement() {
        BunkerEngagements engagements = new BunkerEngagements();

        engagements.onSample(100, bunker(350), units(BunkerEngagements.MIN_UNITS - 1, NEAR));
        engagements.onOurDeath(NEAR, true);

        assertTrue(engagements.finish(200).isEmpty());
    }

    @Test
    void anEngagementCountsTheLossesOnBothSidesAndEndsAfterTheGap() {
        BunkerEngagements engagements = new BunkerEngagements();
        engagements.onSample(100, bunker(350), units(BunkerEngagements.MIN_UNITS, NEAR));
        engagements.onOurDeath(NEAR, true);
        engagements.onOurDeath(NEAR, true);
        engagements.onOurDeath(NEAR, false);
        engagements.onEnemyDeath(new Position(1010, 1000));
        engagements.onSample(140, bunker(250), units(1, NEAR));

        assertTrue(engagements.onSample(140 + BunkerEngagements.GAP_FRAMES - 1, bunker(250),
                Collections.emptyList()).isEmpty());
        List<Closed> closed = engagements.onSample(140 + BunkerEngagements.GAP_FRAMES, bunker(250),
                Collections.emptyList());

        assertEquals(1, closed.size());
        Closed engagement = closed.get(0);
        assertEquals(1, engagement.getId());
        assertEquals(BUNKER_ID, engagement.getBunkerId());
        assertEquals(100, engagement.getStartFrame());
        assertEquals(140 + BunkerEngagements.GAP_FRAMES, engagement.getEndFrame());
        assertEquals(3, engagement.getOurLost());
        assertEquals(2, engagement.getLingsLost());
        assertEquals(1, engagement.getEnemyLost());
        assertEquals(350, engagement.getHitPointsStart());
        assertEquals(250, engagement.getHitPointsEnd());
        assertFalse(engagement.isBroken());
    }

    @Test
    void anEngagementEndsBrokenWhenTheBunkerIsNoLongerLiving() {
        BunkerEngagements engagements = new BunkerEngagements();
        engagements.onSample(100, bunker(350), units(BunkerEngagements.MIN_UNITS, NEAR));

        List<Closed> closed = engagements.onSample(160, Collections.emptyList(), units(BunkerEngagements.MIN_UNITS, NEAR));

        assertEquals(1, closed.size());
        assertTrue(closed.get(0).isBroken());
        assertEquals(0, closed.get(0).getHitPointsEnd());
        assertEquals(160, closed.get(0).getEndFrame());
    }

    @Test
    void deathsFarFromTheBunkerAreNotCounted() {
        BunkerEngagements engagements = new BunkerEngagements();
        engagements.onSample(100, bunker(350), units(BunkerEngagements.MIN_UNITS, NEAR));

        engagements.onOurDeath(FAR, true);
        engagements.onEnemyDeath(FAR);
        List<Closed> closed = engagements.finish(200);

        assertEquals(0, closed.get(0).getOurLost());
        assertEquals(0, closed.get(0).getEnemyLost());
    }

    @Test
    void aDeathIsCreditedToTheNearestEngagement() {
        BunkerEngagements engagements = new BunkerEngagements();
        Position second = new Position(1400, 1000);
        List<BunkerSample> two = new ArrayList<>();
        two.add(new BunkerSample(BUNKER_ID, BUNKER_AT, 350));
        two.add(new BunkerSample(8, second, 350));
        List<OurUnit> army = units(BunkerEngagements.MIN_UNITS, new Position(1200, 1000));
        engagements.onSample(100, two, army);

        engagements.onOurDeath(new Position(1300, 1000), true);
        List<Closed> closed = engagements.finish(200);

        assertEquals(2, closed.size());
        Closed first = closed.get(0).getBunkerId() == BUNKER_ID ? closed.get(0) : closed.get(1);
        Closed other = closed.get(0) == first ? closed.get(1) : closed.get(0);
        assertEquals(0, first.getOurLost());
        assertEquals(1, other.getOurLost());
    }

    @Test
    void aSecondEngagementAtTheSameBunkerGetsTheNextId() {
        BunkerEngagements engagements = new BunkerEngagements();
        engagements.onSample(100, bunker(350), units(BunkerEngagements.MIN_UNITS, NEAR));
        engagements.onSample(100 + BunkerEngagements.GAP_FRAMES, bunker(350), Collections.emptyList());

        engagements.onSample(1000, bunker(350), units(BunkerEngagements.MIN_UNITS, NEAR));
        List<Closed> closed = engagements.finish(1100);

        assertEquals(2, closed.get(0).getId());
    }
}
