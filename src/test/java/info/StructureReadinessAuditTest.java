package info;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the line between the two count vocabularies.
 *
 * <p>A structure is readable at three moments — planned, part-built, finished — and a gate has to
 * say which one it means, so structures go through {@link GameState#structureCount} and name a
 * {@link Readiness}. A mobile unit's count already folds in the plans and eggs in flight, so the
 * distinction does not arise for units and the unit helpers answer both questions on their own.
 *
 * <p>The rule was worth writing down because the wrong side of it is silent. Reading finished
 * structures where the gate meant committed ones costs a whole build time and nothing reports it,
 * and the same call spelled the same way is right for a Zergling and wrong for a Spire. This test
 * fails the build instead.
 */
class StructureReadinessAuditTest {

    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

    private static final List<String> UNIT_COUNT_METHODS = Arrays.asList(
            "ourUnitCount",
            "ourLivingUnitCount",
            "queuedUnitPlanCount",
            "outstandingUnitPlanCount");

    private static final String STRUCTURE_COUNT_METHOD = "structureCount";

    private static final Pattern UNIT_TYPE = Pattern.compile("\\bZerg_[A-Za-z_]+");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String withoutComments(Path path) throws IOException {
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll(" ");
    }

    private static int endOfArguments(String source, int openParen) {
        int depth = 0;
        for (int i = openParen; i < source.length(); i += 1) {
            char character = source.charAt(i);
            if (character == '(') {
                depth += 1;
            } else if (character == ')') {
                depth -= 1;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> argumentListsOf(String source, String methodName) {
        List<String> argumentLists = new ArrayList<>();
        String call = methodName + "(";
        int from = 0;
        while (true) {
            int start = source.indexOf(call, from);
            if (start < 0) {
                return argumentLists;
            }
            int openParen = start + call.length() - 1;
            int closeParen = endOfArguments(source, openParen);
            from = openParen + 1;
            if (closeParen > 0 && !Character.isJavaIdentifierPart(charBefore(source, start))) {
                argumentLists.add(source.substring(openParen + 1, closeParen));
            }
        }
    }

    private static char charBefore(String source, int index) {
        return index == 0 ? ' ' : source.charAt(index - 1);
    }

    private static List<String> unitTypesIn(String argumentList) {
        List<String> unitTypes = new ArrayList<>();
        Matcher matcher = UNIT_TYPE.matcher(argumentList);
        while (matcher.find()) {
            unitTypes.add(matcher.group());
        }
        return unitTypes;
    }

    private static boolean isStructure(String unitTypeName) {
        return UnitType.valueOf(unitTypeName).isBuilding();
    }

    private static String describe(Path path, String methodName, String unitTypeName) {
        return SOURCE_ROOT.relativize(path) + ": " + methodName + "(.. " + unitTypeName + " ..)";
    }

    @Test
    void routesEveryStructureCountThroughAStatedReadiness() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String source = withoutComments(path);
            for (String methodName : UNIT_COUNT_METHODS) {
                for (String argumentList : argumentListsOf(source, methodName)) {
                    for (String unitTypeName : unitTypesIn(argumentList)) {
                        if (isStructure(unitTypeName)) {
                            offenders.add(describe(path, methodName, unitTypeName));
                        }
                    }
                }
            }
        }

        assertEquals(
                Collections.emptyList(),
                offenders,
                "a structure counted without naming a Readiness; call structureCount(Readiness, ..) instead");
    }

    @Test
    void keepsMobileUnitsOffTheStructureCount() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String source = withoutComments(path);
            for (String argumentList : argumentListsOf(source, STRUCTURE_COUNT_METHOD)) {
                for (String unitTypeName : unitTypesIn(argumentList)) {
                    if (!isStructure(unitTypeName)) {
                        offenders.add(describe(path, STRUCTURE_COUNT_METHOD, unitTypeName));
                    }
                }
            }
        }

        assertEquals(
                Collections.emptyList(),
                offenders,
                "a mobile unit passed to structureCount; its own count already folds in plans and eggs");
    }

    @Test
    void readsTheSourcesItClaimsToAudit() throws IOException {
        List<Path> sources = sources();

        assertTrue(sources.size() > 100, "expected the main source tree, found " + sources.size() + " files");
        assertTrue(
                argumentListsOf(withoutComments(SOURCE_ROOT.resolve("info/GameState.java")), STRUCTURE_COUNT_METHOD).size() > 1,
                "expected GameState to call structureCount");
    }
}
