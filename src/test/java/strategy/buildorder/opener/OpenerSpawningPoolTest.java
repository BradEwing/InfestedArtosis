package strategy.buildorder.opener;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every opener owns its Spawning Pool request. A build order that left the pool to whichever
 * terminal build order it transitions into would lose the pool outright whenever a rule blocked
 * the transition, and with it every zergling, sunken and reaction that a standing pool unlocks.
 *
 * <p>3HatchBeforePool is the one opener that defers the request, and it defers it explicitly:
 * it queues the pool itself once its third hatchery is claimed.
 */
class OpenerSpawningPoolTest {

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

    private static final String POOL_REQUEST = "planSpawningPool(";

    private static final int START_LOCATIONS = 4;

    private static final int DEFERRED_POOL_DRONES = 13;

    private static final int TWO_BASES = 2;

    private static final int THREE_BASES = 3;

    private static List<BuildOrder> openers() {
        return Arrays.asList(
                new FourPool(),
                new NinePoolSpeed(),
                new Overpool(),
                new ThreeHatchBeforePool(),
                new TwelveHatch(),
                new TwelvePool());
    }

    private static String sourceOf(BuildOrder opener) throws IOException {
        Path path = SOURCE_ROOT.resolve(opener.getClass().getName().replace('.', '/') + ".java");
        assertTrue(Files.exists(path), path.toString());
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    void coversEveryOpenerTheFactoryOffers() {
        Set<String> named = new HashSet<>();
        for (BuildOrder opener : openers()) {
            named.add(opener.getName());
        }

        assertEquals(named, new BuildOrderFactory(START_LOCATIONS, Race.Terran).getOpenerNames());
    }

    @Test
    void everyOpenerRequestsItsOwnSpawningPool() throws IOException {
        for (BuildOrder opener : openers()) {
            assertTrue(sourceOf(opener).contains(POOL_REQUEST), opener.getName());
        }
    }

    @Test
    void theDeferredOpenerHoldsItsPoolUntilItsThirdHatchery() {
        assertFalse(ThreeHatchBeforePool.shouldPlanPool(DEFERRED_POOL_DRONES, TWO_BASES, true));
        assertTrue(ThreeHatchBeforePool.shouldPlanPool(DEFERRED_POOL_DRONES, THREE_BASES, true));
        assertFalse(ThreeHatchBeforePool.shouldPlanPool(DEFERRED_POOL_DRONES - 1, THREE_BASES, true));
        assertFalse(ThreeHatchBeforePool.shouldPlanPool(DEFERRED_POOL_DRONES, THREE_BASES, false));
    }
}
