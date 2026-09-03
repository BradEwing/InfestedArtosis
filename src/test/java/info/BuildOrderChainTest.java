package info;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOrderChainTest {

    @Test
    void joinsEntriesInOrder() {
        BuildOrderChain chain = new BuildOrderChain();
        chain.add("2HatchMuta");
        chain.add("3HatchLurker");

        assertEquals("2HatchMuta;3HatchLurker", chain.join());
        assertFalse(chain.isEmpty());
    }

    @Test
    void emptyChainJoinsToBlankString() {
        assertTrue(new BuildOrderChain().isEmpty());
        assertEquals("", new BuildOrderChain().join());
    }

    @Test
    void consecutiveRepeatsCollapse() {
        BuildOrderChain chain = new BuildOrderChain();
        chain.add("2HatchMuta");
        chain.add("2HatchMuta");

        assertEquals("2HatchMuta", chain.join());
    }

    @Test
    void repeatedNamesAfterAnotherEntryAreKept() {
        BuildOrderChain chain = new BuildOrderChain();
        chain.add("2HatchMuta");
        chain.add("3HatchLurker");
        chain.add("2HatchMuta");

        assertEquals("2HatchMuta;3HatchLurker;2HatchMuta", chain.join());
    }

    @Test
    void nullAndBlankNamesAreIgnored() {
        BuildOrderChain chain = new BuildOrderChain();
        chain.add(null);
        chain.add("");

        assertTrue(chain.isEmpty());
    }

    @Test
    void chainLengthIsCapped() {
        BuildOrderChain chain = new BuildOrderChain();
        for (int i = 0; i < 20; i++) {
            chain.add("Build" + i);
        }

        assertEquals(8, chain.join().split(";", -1).length);
    }
}
