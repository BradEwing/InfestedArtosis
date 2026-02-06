package learning;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * Utility class for calculating weighted UCB scores that prioritize more granular data.
 * Handles the logic for combining map-specific and opponent-specific build order history.
 * 
 * Uses a sigmoid curve to dynamically weight the map-specific and opponent-specific data.
 * 
 * Defaults to opponent-only data if no map-specific data is available, final fallback is pure exploration.
 * </p>
 */
public class WeightedUCBCalculator {

    private static final double MAX_MAP_WEIGHT = 0.8;
    private static final int CONFIDENCE_THRESHOLD = 10;
    private static final double SIGMOID_STEEPNESS = -0.3;
    private static final double EXPLORATION_NOISE_RANGE = 0.2;
    private static final double EXPLORATION_NOISE_OFFSET = 0.1;

    public static double calculateWeightedScore(String strategy,
            String mapName,
            String opponentName,
            Map<String, MapAwareRecord> mapSpecificRecords,
            Map<String, Record> opponentRecords,
            int totalGames) {

        String mapKey = createMapKey(mapName, strategy);
        MapAwareRecord mapRecord = mapSpecificRecords.get(mapKey);
        Record opponentRecord = opponentRecords.get(strategy);

        if (mapRecord != null && mapRecord.games() > 0) {
            int mapGames = mapRecord.games();

            
            double confidence = 1.0 / (1.0 + Math.exp(SIGMOID_STEEPNESS * (mapGames - CONFIDENCE_THRESHOLD)));
            double mapWeight = MAX_MAP_WEIGHT * confidence;
            double opponentWeight = 1.0 - mapWeight;

            double mapScore = mapRecord.index(totalGames);
            double opponentScore = (opponentRecord != null && opponentRecord.games() > 0)
                    ? opponentRecord.index(totalGames)
                    : 0.0;

            return mapWeight * mapScore + opponentWeight * opponentScore;
        }

        if (opponentRecord != null && opponentRecord.games() > 0) {
            return opponentRecord.index(totalGames);
        }

        if (totalGames == 0) {
            return Math.random();
        }
        
        return Math.sqrt(Math.log(totalGames)) + (Math.random() * EXPLORATION_NOISE_RANGE - EXPLORATION_NOISE_OFFSET);
    }
    
    public static String findBestStrategy(List<String> candidates,
                                        String mapName,
                                        String opponentName,
                                        Map<String, MapAwareRecord> mapSpecificRecords,
                                        Map<String, Record> opponentRecords,
                                        int totalGames) {
        
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
                                               mapSpecificRecords, opponentRecords, totalGames);
            
            if (score > bestScore) {
                bestScore = score;
                bestStrategy = strategy;
            }
        }
        
        return bestStrategy;
    }
    
    public static String createMapKey(String mapName, String strategy) {
        return mapName + "_" + strategy;
    }
}
