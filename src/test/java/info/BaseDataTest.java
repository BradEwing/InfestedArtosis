package info;

import bwem.Base;
import info.map.GroundPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BaseData expansion selection.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so BaseData is exercised with no bases at all.
 */
public class BaseDataTest {

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
