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
     * Strips the characters that would break a quote-free CSV.
     */
    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(',', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static String name(Object value) {
        if (value == null) {
            return "NONE";
        }
        return sanitize(value.toString());
    }
}
