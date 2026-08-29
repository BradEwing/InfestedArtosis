package telemetry;

import java.util.Locale;

/**
 * Formatting helpers shared by the telemetry rows.
 *
 * Doubles are always formatted under Locale.ROOT: a comma decimal separator from a European
 * default locale would shift every field after it in the row.
 */
final class Csv {

    private Csv() {}

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /**
     * Strips the characters that would break a quote-free CSV. The double quote is included
     * because a field that opens with one puts a reader into quoted mode and swallows the rest of
     * the row.
     */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }

    /**
     * Formats a BWAPI half-supply count as real supply. BWAPI counts supply in half units, so
     * integer division silently rounds a zergling pair down and loses the half a lone zergling
     * costs.
     */
    static String halfSupply(int halfUnits) {
        int whole = halfUnits / 2;
        if (halfUnits % 2 == 0) {
            return Integer.toString(whole);
        }
        return whole + ".5";
    }

    static String name(Object value) {
        if (value == null) {
            return "NONE";
        }
        return sanitize(value.toString());
    }
}
