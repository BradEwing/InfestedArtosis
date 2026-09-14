package unit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LairSiteTest {
    private static final int MAIN_X = 7;
    private static final int MAIN_Y = 118;

    @Test
    void theMainHatcheryTakesTheLairWhenMainAndNaturalAreFree() {
        Hatch natural = new Hatch(4, 49, 7, true);
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, true);

        assertSame(main, choose(Arrays.asList(natural, main)));
    }

    @Test
    void theNaturalTakesTheLairWhenTheMainIsBusy() {
        Hatch natural = new Hatch(4, 49, 7, true);
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, false);

        assertSame(natural, choose(Arrays.asList(main, natural)));
    }

    @Test
    void noHatcheryIsChosenWhenNoneIsFree() {
        Hatch natural = new Hatch(4, 49, 7, false);
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, false);

        assertNull(choose(Arrays.asList(main, natural)));
    }

    @Test
    void aMacroHatcheryInTheMainIsPreferredOverTheNaturalWhenTheMainIsBusy() {
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, false);
        Hatch natural = new Hatch(4, 49, 7, true);
        Hatch macro = new Hatch(12, MAIN_X + 6, MAIN_Y - 2, true);

        assertSame(macro, choose(Arrays.asList(natural, main, macro)));
    }

    @Test
    void theChoiceIsTheSameForEveryIterationOrder() {
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, true);
        Hatch natural = new Hatch(4, 49, 7, true);
        Hatch macro = new Hatch(12, MAIN_X + 6, MAIN_Y - 2, true);
        Hatch third = new Hatch(20, 90, 60, true);
        List<Hatch> hatcheries = new ArrayList<>(Arrays.asList(main, natural, macro, third));

        for (List<Hatch> order : permutations(hatcheries)) {
            assertSame(main, choose(order));
            assertSame(main, choose(new LinkedHashSet<>(order)));
        }
    }

    @Test
    void equallyDistantHatcheriesAreBrokenByUnitIdForEveryIterationOrder() {
        Hatch main = new Hatch(9, MAIN_X, MAIN_Y, false);
        Hatch left = new Hatch(31, MAIN_X - 10, MAIN_Y, true);
        Hatch right = new Hatch(17, MAIN_X + 10, MAIN_Y, true);

        for (List<Hatch> order : permutations(Arrays.asList(main, left, right))) {
            assertSame(right, choose(order));
        }
    }

    private static Hatch choose(Collection<Hatch> hatcheries) {
        return LairSite.choose(
                hatcheries,
                h -> h.free,
                h -> h.x == MAIN_X && h.y == MAIN_Y,
                h -> Math.hypot(h.x - MAIN_X, h.y - MAIN_Y),
                h -> h.id);
    }

    private static List<List<Hatch>> permutations(List<Hatch> items) {
        if (items.isEmpty()) {
            return Collections.singletonList(new ArrayList<>());
        }
        List<List<Hatch>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<Hatch> rest = new ArrayList<>(items);
            Hatch head = rest.remove(i);
            for (List<Hatch> tail : permutations(rest)) {
                tail.add(0, head);
                result.add(tail);
            }
        }
        return result;
    }

    private static final class Hatch {
        private final int id;
        private final int x;
        private final int y;
        private final boolean free;

        Hatch(int id, int x, int y, boolean free) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.free = free;
        }
    }
}
