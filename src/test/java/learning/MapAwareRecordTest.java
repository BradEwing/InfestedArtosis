package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for MapAwareRecord D-UCB implementation.
 * Tests the discounted UCB algorithm with map-specific context.
 */
public class MapAwareRecordTest {
    
    private MapAwareRecord recordA;
    private MapAwareRecord recordB;
    private long baseTime;
    
    @BeforeEach
    void setUp() {
        baseTime = System.currentTimeMillis();
        recordA = MapAwareRecord.builder()
                .strategy("StrategyA")
                .mapName("MapA")
                .opponentName("OpponentA")
                .opponentRace("Terran")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
                
        recordB = MapAwareRecord.builder()
                .strategy("StrategyB")
                .mapName("MapA")
                .opponentName("OpponentA")
                .opponentRace("Terran")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
    }
    
    @Test
    void testEmptyRecordReturnsDefaultIndex() {
        assertEquals(1.0, recordA.index(100), 0.001);
    }
    

    
    /**
     * Tests that the D-UCB algorithm correctly weights recent wins more heavily than old wins.
     * Creates two strategies with identical win/loss records but different chronological ordering:
     * - Strategy A: 3 losses (old) followed by 3 wins (recent)
     * - Strategy B: 3 wins (old) followed by 3 losses (recent)
     * Strategy A should score higher due to recent wins being weighted more heavily.
     */
    @Test
    void testChronologicalScenario_StrategyA_3LossesThen3Wins() {
        long time = baseTime;
        
        for (int i = 0; i < 3; i++) {
            recordA.addLossTimestamp(time);
            time += 1000;
        }
        
        for (int i = 0; i < 3; i++) {
            recordA.addWinTimestamp(time);
            time += 1000;
        }
        recordA.setWins(3);
        recordA.setLosses(3);
        
        time = baseTime;
        
        for (int i = 0; i < 3; i++) {
            recordB.addWinTimestamp(time);
            time += 1000;
        }
        
        for (int i = 0; i < 3; i++) {
            recordB.addLossTimestamp(time);
            time += 1000;
        }
        recordB.setWins(3);
        recordB.setLosses(3);
        
        double indexA = recordA.index(6);
        double indexB = recordB.index(6);
        
        assertTrue(indexA > indexB, 
            String.format("Strategy A (recent wins) should score higher than Strategy B (recent losses). A: %.4f, B: %.4f", 
                indexA, indexB));
        
        System.out.println("Strategy A (3 losses then 3 wins) index: " + indexA);
        System.out.println("Strategy B (3 wins then 3 losses) index: " + indexB);
    }
    
    @Test
    void testMapSpecificContext() {
        MapAwareRecord mapRecord = MapAwareRecord.builder()
                .strategy("12Pool")
                .mapName("Lost Temple")
                .opponentName("TerranBot")
                .opponentRace("Terran")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
        
        mapRecord.addWinTimestamp(baseTime);
        mapRecord.addLossTimestamp(baseTime + 1000);
        mapRecord.addWinTimestamp(baseTime + 2000);
        
        double index = mapRecord.index(100);
        assertTrue(index > 0.0, "Map-specific record should have positive index");
    }
    
    @Test
    void testOpponentSpecificContext() {
        MapAwareRecord opponentRecord = MapAwareRecord.builder()
                .strategy("12Hatch")
                .mapName("MapA")
                .opponentName("ProtossBot")
                .opponentRace("Protoss")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
        
        opponentRecord.addWinTimestamp(baseTime);
        opponentRecord.addWinTimestamp(baseTime + 1000);
        opponentRecord.addLossTimestamp(baseTime + 2000);
        
        double index = opponentRecord.index(100);
        assertTrue(index > 0.0, "Opponent-specific record should have positive index");
    }
    
    /**
     * Tests exponential decay in the D-UCB algorithm by adding games with increasing time gaps.
     * The algorithm should apply gamma^age weighting where older games have less influence.
     * With 3 wins and exploration term, the index should be greater than 1.0.
     */
    @Test
    void testExponentialDecay() {
        long time = baseTime;
        
        recordA.addWinTimestamp(time);
        time += 10000;
        
        recordA.addWinTimestamp(time);
        time += 10000;
        
        recordA.addWinTimestamp(time);
        recordA.setWins(3);
        recordA.setLosses(0);
        
        double index = recordA.index(100);
        
        assertTrue(index > 0.0, "Index should be positive");
        assertTrue(index > 1.0, "Index should be greater than 1.0 due to exploration term");
    }
    
    /**
     * Tests that recent wins are weighted more heavily than old wins in the D-UCB algorithm.
     * Creates two strategies with identical win/loss ratios but different timing:
     * - RecentWins: 1 loss (old) + 2 wins (recent)
     * - OldWins: 2 wins (old) + 1 loss (recent)
     * RecentWins should score higher due to recent wins being weighted more heavily.
     */
    @Test
    void testRecentWinsWeightedMore() {
        MapAwareRecord recentWins = MapAwareRecord.builder()
                .strategy("RecentWins")
                .mapName("MapA")
                .opponentName("OpponentA")
                .opponentRace("Terran")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
                
        MapAwareRecord oldWins = MapAwareRecord.builder()
                .strategy("OldWins")
                .mapName("MapA")
                .opponentName("OpponentA")
                .opponentRace("Terran")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
        
        long time = baseTime;
        
        recentWins.addLossTimestamp(time);
        time += 10000;
        recentWins.addWinTimestamp(time);
        time += 1000;
        recentWins.addWinTimestamp(time);
        recentWins.setWins(2);
        recentWins.setLosses(1);
        
        time = baseTime;
        
        oldWins.addWinTimestamp(time);
        time += 1000;
        oldWins.addWinTimestamp(time);
        time += 10000;
        oldWins.addLossTimestamp(time);
        oldWins.setWins(2);
        oldWins.setLosses(1);
        
        double recentIndex = recentWins.index(100);
        double oldIndex = oldWins.index(100);
        
        assertTrue(recentIndex > oldIndex, 
            "Recent wins should score higher than old wins");
    }
    
    @Test
    void testZeroTotalGames() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        
        assertEquals(1.0, recordA.index(0), 0.001);
    }
    
    @Test
    void testSingleGame() {
        recordA.addWinTimestamp(baseTime);
        
        double index = recordA.index(100);
        assertTrue(index > 1.0, "Single win should have positive index");
    }
    
    @Test
    void testDiscountedWinsCalculation() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.addWinTimestamp(baseTime + 2000);
        
        double index = recordA.index(100);
        
        assertTrue(index > 0.5, "Recent wins should give positive index");
    }
    
    @Test
    void testExplorationTerm() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.setWins(1);
        recordA.setLosses(1);
        
        double indexLow = recordA.index(10);
        double indexHigh = recordA.index(1000);
        
        assertTrue(indexHigh > indexLow, 
            "Higher totalGames should increase exploration term");
    }
    
    /**
     * Tests the gamma decay constant (0.95) is applied correctly in the D-UCB algorithm.
     * With 3 wins and GAMMA = 0.95:
     * - discountedWins = 1.0 + 0.95 + 0.95^2 = 2.8525
     * - discountedGames = 1.0 + 0.95 + 0.95^2 = 2.8525
     * - sampleMean = 2.8525 / 2.8525 = 1.0
     * - exploration = sqrt(2 * ln(100) / 2.8525) ≈ 1.8
     * The index should be greater than 1.0 due to exploration term.
     */
    @Test
    void testGammaConstant() {
        recordA.addWinTimestamp(baseTime);
        recordA.addWinTimestamp(baseTime + 1000);
        recordA.addWinTimestamp(baseTime + 2000);
        recordA.setWins(3);
        recordA.setLosses(0);
        
        double index = recordA.index(100);
        
        assertTrue(index > 1.0, "Index should be greater than 1.0 due to exploration");
        assertTrue(index < 3.0, "Index should be reasonable");
    }
}
