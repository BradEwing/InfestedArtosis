package strategy.buildorder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Creep and Sunken or Spore Colony pair is planned by one method each, and both methods
 * read the prerequisite gate their seam tests cover. GameState cannot be built in a unit test,
 * so the bodies are pinned by source: a merge that loops on the raw target again, or forms a
 * Spore pair without asking {@link BuildOrder#sporeStep}, fails here.
 */
class ColonyPrerequisiteGateTest {

    private static final Path BUILD_ORDER_SOURCE = Paths.get("src", "main", "java", "strategy", "buildorder", "BuildOrder.java");

    private static final String PLAN_SUNKEN_COLONY = "protected Set<Plan> planSunkenColony(GameState gameState, int priority, int target)";

    private static final String PLAN_SPORE_COLONY = "protected Set<Plan> planSporeColony(GameState gameState)";

    private static final String METHOD_END = "\n    }\n";

    @Test
    void planSunkenColonyLoopsOnTheSunkenPairBudget() throws IOException {
        String body = methodBody(PLAN_SUNKEN_COLONY);

        assertTrue(body.contains("int budget = sunkenPairBudget(gameState.getTechProgression(), target);"), body);
        assertTrue(body.contains("while (planned < budget)"), body);
        assertFalse(body.contains("while (planned < target)"), body);
    }

    @Test
    void planSporeColonyFormsAPairOnlyOnTheSporeColonyStep() throws IOException {
        String body = methodBody(PLAN_SPORE_COLONY);

        assertTrue(body.contains("SporeStep step = sporeStep(techProgression);"), body);
        assertTrue(body.contains("if (step != SporeStep.SPORE_COLONY)"), body);
        assertTrue(body.indexOf("if (step != SporeStep.SPORE_COLONY)") < body.indexOf("reserveSporeColony("), body);
    }

    private static String methodBody(String signature) throws IOException {
        String source = new String(Files.readAllBytes(BUILD_ORDER_SOURCE), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int end = source.indexOf(METHOD_END, start);
        assertTrue(end > start, signature);
        return source.substring(start, end);
    }
}
