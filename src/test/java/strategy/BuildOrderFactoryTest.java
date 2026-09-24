package strategy;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOrderFactoryTest {

    @Test
    void buildOrderNamesNeverContainCsvOrChainDelimiters() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Terran);

        for (String name : factory.getAllBuildOrderNames()) {
            assertFalse(name.contains(",") || name.contains(";") || name.contains("\"") || name.contains("\n"),
                    "Build order name breaks the learning CSV invariants: " + name);
        }
    }

    @Test
    void registersNineHatchAsAPlayableOpenerAgainstEveryKnownRace() {
        for (Race race : new Race[]{Race.Protoss, Race.Terran, Race.Zerg}) {
            BuildOrderFactory factory = new BuildOrderFactory(4, race);

            assertTrue(factory.getOpenerNames().contains("9Hatch"));
            assertTrue(factory.isPlayableOpener(factory.getByName("9Hatch")));
        }
    }

    @Test
    void leavesNineHatchOutAgainstAnUnknownRace() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Unknown);

        assertFalse(factory.getOpenerNames().contains("9Hatch"));
    }
}
