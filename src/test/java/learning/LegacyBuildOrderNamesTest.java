package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyBuildOrderNamesTest {

    @Test
    void theLegacyNameMapsToTheVariantOfTheFilesRace() {
        assertEquals("SpeedlingT", LegacyBuildOrderNames.resolve("SpeedlingAllIn", "Terran"));
        assertEquals("SpeedlingP", LegacyBuildOrderNames.resolve("SpeedlingAllIn", "Protoss"));
        assertEquals("SpeedlingZ", LegacyBuildOrderNames.resolve("SpeedlingAllIn", "Zerg"));
        assertEquals("SpeedlingR", LegacyBuildOrderNames.resolve("SpeedlingAllIn", "Unknown"));
        assertEquals("SpeedlingR", LegacyBuildOrderNames.resolve("SpeedlingAllIn", "Random"));
    }

    @Test
    void theRaceOverloadAgreesWithTheStringForm() {
        assertEquals("SpeedlingT", LegacyBuildOrderNames.resolve("SpeedlingAllIn", Race.Terran));
        assertEquals("SpeedlingR", LegacyBuildOrderNames.resolve("SpeedlingAllIn", Race.Unknown));
    }

    @Test
    void otherNamesPassThrough() {
        assertEquals("3HatchLurker", LegacyBuildOrderNames.resolve("3HatchLurker", "Terran"));
        assertEquals("SpeedlingT", LegacyBuildOrderNames.resolve("SpeedlingT", "Terran"));
        assertNull(LegacyBuildOrderNames.resolve(null, "Terran"));
    }

    @Test
    void everySegmentOfAChainIsMapped() {
        assertEquals("12Hatch;SpeedlingZ", LegacyBuildOrderNames.resolveChain("12Hatch;SpeedlingAllIn", "Zerg"));
        assertEquals("SpeedlingP;3HatchHydra", LegacyBuildOrderNames.resolveChain("SpeedlingAllIn;3HatchHydra", "Protoss"));
    }

    @Test
    void aRandomFilesRowMapsByTheRaceItResolvedTo() {
        assertEquals("Terran", LegacyBuildOrderNames.variantRace("Unknown", "Terran"));
        assertEquals("Zerg", LegacyBuildOrderNames.variantRace("Unknown", "Zerg"));
        assertEquals("Unknown", LegacyBuildOrderNames.variantRace("Unknown", "Unknown"));
        assertEquals("Protoss", LegacyBuildOrderNames.variantRace("Protoss", "Unknown"));
        assertEquals("SpeedlingT", LegacyBuildOrderNames.resolve("SpeedlingAllIn",
                LegacyBuildOrderNames.variantRace("Unknown", "Terran")));
    }

    @Test
    void emptyAndNullChainsAreUnchanged() {
        assertEquals("", LegacyBuildOrderNames.resolveChain("", "Terran"));
        assertNull(LegacyBuildOrderNames.resolveChain(null, "Terran"));
        assertEquals("9PoolSpeed", LegacyBuildOrderNames.resolveChain("9PoolSpeed", "Terran"));
    }
}
