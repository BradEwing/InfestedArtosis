package strategy;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildOrderFactoryTest {

    @Test
    void buildOrderNamesNeverContainCsvOrChainDelimiters() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Terran);

        for (String name : factory.getAllBuildOrderNames()) {
            assertFalse(name.contains(",") || name.contains(";") || name.contains("\"") || name.contains("\n"),
                    "Build order name breaks the learning CSV invariants: " + name);
        }
    }
}
