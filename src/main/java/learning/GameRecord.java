package learning;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    private int frameCount;
    
    public String toCsvRow() {
        return String.format("%d,%s,%d,%s,%s,%s,%s,%s,%s,%d",
            timestamp,
            isWinner,
            numStartingLocations,
            escapeCsvField(mapName),
            escapeCsvField(opponentName),
            escapeCsvField(opponentRace),
            escapeCsvField(opener),
            escapeCsvField(buildOrder),
            escapeCsvField(detectedStrategies),
            frameCount
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
            .frameCount(fields.length > 9 && !fields[9].isEmpty() ? Integer.parseInt(fields[9]) : 0)
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
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
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
                result.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        result.add(currentField.toString());

        return result.toArray(new String[0]);
    }
}
