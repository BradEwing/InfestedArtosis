package info;

import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;
import info.map.GroundPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BaseData expansion selection and geyser reservation release.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so BaseData is exercised with no bases at all, and the reservation tests stand a null in for the geyser
 * unit: the release path identifies a reservation by its stored tile position and calls nothing on the unit.
 */
public class BaseDataTest {

    private static final TilePosition GEYSER_TILE = new TilePosition(20, 30);

    private static final TilePosition OTHER_TILE = new TilePosition(44, 12);

    private BaseData baseData;

    @BeforeEach
    void setUp() {
        baseData = new BaseData(new ArrayList<>());
    }

    private void setAvailableBases(HashMap<Base, GroundPath> bases) throws ReflectiveOperationException {
        Field field = BaseData.class.getDeclaredField("availableBases");
        field.setAccessible(true);
        field.set(baseData, bases);
    }

    /**
     * The reclaim runs while the unit still reports a refinery type, which is what unreserveExtractor
     * refuses to act on, so it has to return the geyser to the available pool on its own.
     */
    @Test
    void releaseReservedGeyserReturnsTheGeyserToTheAvailablePool() {
        HashSet<Unit> reserved = new HashSet<>();
        reserved.add(null);
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertTrue(BaseData.releaseReservedGeyser(reserved, available, positions, GEYSER_TILE));

        assertTrue(reserved.isEmpty());
        assertEquals(1, available.size());
    }

    /**
     * The plan sweep drops the reservation without making the geyser available again, so a release
     * keyed off the reservation set would find nothing and strand the geyser in neither set.
     */
    @Test
    void releaseReservedGeyserRecoversAGeyserThatIsNoLongerReserved() {
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertTrue(BaseData.releaseReservedGeyser(new HashSet<>(), available, positions, GEYSER_TILE));

        assertEquals(1, available.size());
    }

    @Test
    void releaseReservedGeyserReportsNothingNewWhenTheGeyserIsAlreadyAvailable() {
        HashSet<Unit> available = new HashSet<>();
        available.add(null);
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertFalse(BaseData.releaseReservedGeyser(new HashSet<>(), available, positions, GEYSER_TILE));

        assertEquals(1, available.size());
    }

    @Test
    void releaseReservedGeyserIgnoresATileItTracksNoGeyserFor() {
        HashSet<Unit> reserved = new HashSet<>();
        reserved.add(null);
        HashSet<Unit> available = new HashSet<>();
        HashMap<Unit, TilePosition> positions = new HashMap<>();
        positions.put(null, GEYSER_TILE);

        assertFalse(BaseData.releaseReservedGeyser(reserved, available, positions, OTHER_TILE));

        assertEquals(1, reserved.size());
        assertTrue(available.isEmpty());
    }

    @Test
    void releaseReservedGeyserToleratesAMissingTilePosition() {
        assertFalse(BaseData.releaseReservedGeyser(new HashSet<>(), new HashSet<>(), new HashMap<>(), null));
    }

    @Test
    void testFindNewBaseWithNoBasesReturnsNull() {
        assertTrue(baseData.getMyBases().isEmpty());
        assertNull(baseData.findNewBase());
    }

    /**
     * A candidate with no known ground path is not a viable expansion and must simply be skipped.
     */
    @Test
    void testFindNewBaseSkipsCandidatesWithoutAGroundPath() throws ReflectiveOperationException {
        HashMap<Base, GroundPath> pool = new HashMap<>();
        pool.put(null, null);
        setAvailableBases(pool);

        assertNull(baseData.findNewBase());
    }
}
