package macro;

import bwem.Base;
import info.BaseData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the main-base sunken gate.
 *
 * <p>bwem.Base is final with a package-private constructor and bwapi.Unit cannot be instantiated outside its own
 * package, so base counts are simulated by swapping in sets that report a fixed size.
 */
public class ReactionsTest {

    private BaseData baseData;

    @BeforeEach
    void setUp() {
        baseData = new BaseData(new ArrayList<>());
    }

    private static class CountingSet<T> extends HashSet<T> {
        private final int count;

        CountingSet(int count) {
            this.count = count;
        }

        @Override
        public int size() {
            return count;
        }
    }

    private void setBaseCounts(int completed, int reserved) throws ReflectiveOperationException {
        setSetSize("baseHatcheries", completed);
        setSetSize("myBases", completed);
        setSetSize("reservedBases", reserved);
    }

    private void setSetSize(String fieldName, int size) throws ReflectiveOperationException {
        Field field = BaseData.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(baseData, new CountingSet<Base>(size));
    }

    /**
     * The case broken before IA-307: every opener queues the natural around 2:06, which reserved a base and
     * locked the main out of static defense until the hatchery completed.
     */
    @Test
    void testMainIsEligibleWhileNaturalIsOnlyReserved() throws ReflectiveOperationException {
        setBaseCounts(1, 1);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }

    @Test
    void testMainIsEligibleWithASoleBase() throws ReflectiveOperationException {
        setBaseCounts(1, 0);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }

    @Test
    void testMainIsNotMadeEligibleWithTwoCompletedBases() throws ReflectiveOperationException {
        setBaseCounts(2, 0);

        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertFalse(baseData.isAllowSunkenAtMain());
    }

    /**
     * The flag survives until clearMainSunkenOnExpansion runs, so a second call once the natural has
     * completed must not be the thing that clears it.
     */
    @Test
    void testFlagIsNotClearedWhenTheNaturalCompletes() throws ReflectiveOperationException {
        setBaseCounts(1, 1);
        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        setBaseCounts(2, 0);
        Reactions.allowSunkenAtMainIfSingleBase(baseData);

        assertTrue(baseData.isAllowSunkenAtMain());
    }
}
