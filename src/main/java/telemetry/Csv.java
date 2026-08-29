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

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').replace('"', '\'');
    }

    /** Converts BWAPI half-supply units to real supply without rounding. */
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
