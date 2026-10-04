package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.StaticDefenseZone;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LurkerFireZonesTest {

    private static final int RANGE = 192;
    private static final Position BUNKER = new Position(1000, 1000);

    @Test
    void aBunkerWithinTheLurkersRangeIsAnswered() {
        StaticDefenseZone bunker = new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 224);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Collections.singletonList(bunker),
                new Position(1000, 1000 + 100), RANGE);

        assertEquals(Collections.emptyList(), zones);
    }

    @Test
    void aSiegedTankBeyondTheLurkersRangeIsNotAnswered() {
        StaticDefenseZone tank = new StaticDefenseZone(UnitType.Terran_Siege_Tank_Siege_Mode, BUNKER, 384);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Collections.singletonList(tank),
                new Position(1000, 1000 + 300), RANGE);

        assertSame(tank, zones.get(0));
    }

    @Test
    void onlyTheZonesBeyondTheRangeAreKept() {
        StaticDefenseZone near = new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 224);
        StaticDefenseZone far = new StaticDefenseZone(UnitType.Terran_Bunker, new Position(1000, 1600), 224);

        List<StaticDefenseZone> zones = SquadManager.unanswered(Arrays.asList(near, far),
                new Position(1000, 1100), RANGE);

        assertEquals(Collections.singletonList(far), zones);
    }
}
