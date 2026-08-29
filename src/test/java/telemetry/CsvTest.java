package telemetry;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void doublesUseADotUnderACommaDecimalLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.2500", Csv.format(1.25));
            assertEquals("-1.0000", Csv.format(-1));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void sanitizeStripsSeparatorsThatWouldShiftColumns() {
        assertEquals("Fastest 1.16 (4)", Csv.sanitize("Fastest 1.16 (4)"));
        assertEquals("a b", Csv.sanitize("a,b"));
        assertEquals("a b c", Csv.sanitize("a\nb\rc"));
        assertEquals("'quoted' text", Csv.sanitize("\"quoted\" text"));
        assertEquals("", Csv.sanitize(null));
    }

    @Test
    void halfSupplyKeepsTheHalf() {
        assertEquals("0", Csv.halfSupply(0));
        assertEquals("4", Csv.halfSupply(8));
        assertEquals("4.5", Csv.halfSupply(9));
        assertEquals("199.5", Csv.halfSupply(399));
    }

    @Test
    void nameFallsBackToNoneForNull() {
        assertEquals("NONE", Csv.name(null));
        assertEquals("Zerg_Zergling", Csv.name(bwapi.UnitType.Zerg_Zergling));
    }
}
