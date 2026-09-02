package learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Utility class for calculating weighted UCB scores that prioritize more granular data.
 * Handles the logic for combining map-specific and opponent-specific build order history.
 * 
 * Uses a sigmoid curve to dynamically weight the map-specific and opponent-specific data. The
 * sigmoid reads the same discounted evidence the map score is built from, so a record cannot draw
 * blend weight from games its own discount has already forgotten.
 * 
 * Defaults to opponent-only data if no map-specific data is available, final fallback is pure exploration.
 * </p>
 */
public class WeightedUCBCalculator {
    
    private static final double MAX_MAP_WEIGHT = 0.8; 
    private static final int CONFIDENCE_THRESHOLD = 10;

    public static double calculateWeightedScore(String strategy,
            String mapName,
            String opponentName,
            Map<String, MapAwareRecord> mapSpecificRecords,
            Map<String, Record> opponentRecords,
            int totalGames,
            List<Long> gameTimestamps) {

        String mapKey = createMapKey(mapName, strategy);
        MapAwareRecord mapRecord = mapSpecificRecords.get(mapKey);
        Record opponentRecord = opponentRecords.get(strategy);

        if (mapRecord != null && mapRecord.games() > 0) {
            List<Long> mapClock = mapClock(mapSpecificRecords, mapName);
            double mapGames = mapRecord.discountedGames(mapClock);

            double confidence = 1.0 / (1.0 + Math.exp(-0.3 * (mapGames - CONFIDENCE_THRESHOLD)));
            double mapWeight = MAX_MAP_WEIGHT * confidence;
            double opponentWeight = 1.0 - mapWeight;

            double mapScore = mapRecord.index(totalGames, mapClock);
            double opponentScore = (opponentRecord != null && opponentRecord.games() > 0)
                    ? opponentRecord.index(totalGames, gameTimestamps)
                    : 0.0;

            return mapWeight * mapScore + opponentWeight * opponentScore;
        }

        if (opponentRecord != null && opponentRecord.games() > 0) {
            return opponentRecord.index(totalGames, gameTimestamps);
        }

        if (totalGames == 0) {
            return Math.random();
        }
        
        return Math.sqrt(Math.log(totalGames)) + (Math.random() * 0.2 - 0.1);
    }
    
    public static String findBestStrategy(List<String> candidates,
                                        String mapName,
                                        String opponentName,
                                        Map<String, MapAwareRecord> mapSpecificRecords,
                                        Map<String, Record> opponentRecords,
                                        int totalGames,
                                        List<Long> gameTimestamps) {
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        
        String bestStrategy = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (String strategy : candidates) {
            double score = calculateWeightedScore(strategy, mapName, opponentName,
                    mapSpecificRecords, opponentRecords, totalGames, gameTimestamps);
            
            if (score > bestScore) {
                bestScore = score;
                bestStrategy = strategy;
            }
        }
        
        return bestStrategy;
    }
    
    /**
     * Every game played on this map, across all strategies. A map record ages on this clock rather
     * than the opponent's, so one appearance of the map costs one game of decay instead of the
     * fourteen or so opponent games that separate two visits.
     */
    static List<Long> mapClock(Map<String, MapAwareRecord> mapSpecificRecords, String mapName) {
        List<Long> clock = new ArrayList<>();
        if (mapSpecificRecords == null) {
            return clock;
        }
        for (MapAwareRecord record : mapSpecificRecords.values()) {
            if (mapName != null && mapName.equals(record.getMapName())) {
                clock.addAll(record.getWinTimestamps());
                clock.addAll(record.getLossTimestamps());
            }
        }
        return clock;
    }

    public static String createMapKey(String mapName, String strategy) {
        return mapName + "_" + strategy;
    }
}
