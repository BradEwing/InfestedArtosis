package learning;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameRecord {
    private long timestamp;
    private int numStartingLocations;
    private String mapName;
    private String opponentName;
    private String opponentRace;
    private String opener;
    private String buildOrder;
    private String detectedStrategies;
    private boolean isWinner;
    
    public String toCsvRow() {
        return String.format("%d,%s,%d,%s,%s,%s,%s,%s,%s",
            timestamp,
            isWinner,
            numStartingLocations,
            escapeCsvField(mapName),
            escapeCsvField(opponentName),
            escapeCsvField(opponentRace),
            escapeCsvField(opener),
            escapeCsvField(buildOrder),
            escapeCsvField(detectedStrategies)
        );
    }
    
    public static GameRecord fromCsvRow(String csvRow) {
        String[] fields = parseCsvRow(csvRow);
        return GameRecord.builder()
            .timestamp(Long.parseLong(fields[0]))
            .isWinner(Boolean.parseBoolean(fields[1]))
            .numStartingLocations(Integer.parseInt(fields[2]))
            .mapName(fields[3])
            .opponentName(fields[4])
            .opponentRace(fields[5])
            .opener(fields[6])
            .buildOrder(fields[7])
            .detectedStrategies(fields[8])
            .build();
    }
    
    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
    
    private static String[] parseCsvRow(String csvRow) {
        String[] result = new String[9];
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        int fieldIndex = 0;
        
        for (int i = 0; i < csvRow.length(); i++) {
            char c = csvRow.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < csvRow.length() && csvRow.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result[fieldIndex++] = currentField.toString();
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        result[fieldIndex] = currentField.toString();
        
        return result;
    }
}
