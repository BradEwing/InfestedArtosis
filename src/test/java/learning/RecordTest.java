package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

/**
 * Unit tests for Record D-UCB implementation.
 * Tests the discounted UCB algorithm for strategy performance tracking.
 */
public class RecordTest {
    
    private Record recordA;
    private Record recordB;
    private long baseTime;
    
    @BeforeEach
    void setUp() {
        baseTime = System.currentTimeMillis();
        recordA = Record.builder()
                .opener("12Hatch")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
                
        recordB = Record.builder()
                .opener("12Pool")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
    }
    
    @Test
    void testEmptyRecordReturnsDefaultIndex() {
        double index = recordA.index(0);
        assertTrue(index >= 0.0 && index <= 1.0, "Empty record with zero totalGames should return random value between 0 and 1");
    }
    
    /**
     * Tests that the D-UCB algorithm correctly weights recent wins more heavily than old wins.
     * Creates two strategies with identical win/loss records but different chronological ordering:
     * - Strategy A: 3 losses (old) followed by 3 wins (recent)
     * - Strategy B: 3 wins (old) followed by 3 losses (recent)
     * Strategy A should score higher due to recent wins being weighted more heavily.
     */
    @Test
    void testChronologicalScenarioStrategyA3LossesThen3Wins() {
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
    void testBasicRecordOperations() {
        Record record = Record.builder()
                .opener("12Hatch")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
        
        record.addWinTimestamp(baseTime);
        record.addLossTimestamp(baseTime + 1000);
        record.addWinTimestamp(baseTime + 2000);
        
        double index = record.index(100);
        assertTrue(index > 0.0, "Basic record should have positive index");
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
        Record recentWins = Record.builder()
                .opener("RecentWins")
                .wins(0)
                .losses(0)
                .winTimestamps(new ArrayList<>())
                .lossTimestamps(new ArrayList<>())
                .build();
                
        Record oldWins = Record.builder()
                .opener("OldWins")
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
        
        double index = recordA.index(0);
        assertTrue(index >= 0.0 && index <= 1.0, "Zero totalGames should return random value between 0 and 1");
    }
    
    @Test
    void testSingleGame() {
        recordA.addWinTimestamp(baseTime);
        recordA.setWins(1);
        recordA.setLosses(0);
        
        double index = recordA.index(1);
        assertTrue(index > 0.0, "Single win should have positive index");
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
    
    @Test
    void testNetWins() {
        recordA.setWins(5);
        recordA.setLosses(3);
        assertEquals(2, recordA.netWins());
    }
    
    @Test
    void testWinsSquared() {
        recordA.setWins(4);
        assertEquals(16, recordA.winsSquared());
    }
    
    @Test
    void testGames() {
        recordA.setWins(3);
        recordA.setLosses(2);
        assertEquals(5, recordA.games());
    }
    
    @Test
    void testWins() {
        recordA.setWins(7);
        assertEquals(7, recordA.wins());
    }
    
    @Test
    void testEmptyRecordWithZeroGames() {
        double index = recordA.index(0);
        assertTrue(index >= 0.0 && index <= 1.0, "Empty record with zero totalGames should return random value between 0 and 1");
    }
    
    @Test
    void testRecordWithZeroGames() {
        recordA.setWins(0);
        recordA.setLosses(0);
        double index = recordA.index(100);
        double expectedMin = Math.sqrt(Math.log(100)) - 0.1;
        double expectedMax = Math.sqrt(Math.log(100)) + 0.1;
        assertTrue(index >= expectedMin && index <= expectedMax, 
            "Unplayed strategy should return sqrt(ln(totalGames)) + random(-0.1, 0.1)");
    }
    
    @Test
    void testMixedTimestamps() {
        long time = baseTime;
        
        recordA.addWinTimestamp(time);
        time += 5000;
        recordA.addLossTimestamp(time);
        time += 2000;
        recordA.addWinTimestamp(time);
        time += 3000;
        recordA.addLossTimestamp(time);
        
        recordA.setWins(2);
        recordA.setLosses(2);
        
        double index = recordA.index(100);
        assertTrue(index > 0.0, "Mixed timestamps should produce valid index");
    }
    
    @Test
    void testLargeTimeGaps() {
        long time = baseTime;
        
        recordA.addWinTimestamp(time);
        time += 100000;
        recordA.addWinTimestamp(time);
        time += 100000;
        recordA.addWinTimestamp(time);
        
        recordA.setWins(3);
        recordA.setLosses(0);
        
        double index = recordA.index(100);
        assertTrue(index > 1.0, "Large time gaps should still produce valid index");
    }
    
    @Test
    void testAllLosses() {
        long time = baseTime;
        
        for (int i = 0; i < 5; i++) {
            recordA.addLossTimestamp(time);
            time += 1000;
        }
        
        recordA.setWins(0);
        recordA.setLosses(5);
        
        double index = recordA.index(100);
        assertTrue(index > 0.0, "All losses should still produce positive index due to exploration");
    }
    
    @Test
    void testAllWins() {
        long time = baseTime;
        
        for (int i = 0; i < 5; i++) {
            recordA.addWinTimestamp(time);
            time += 1000;
        }
        
        recordA.setWins(5);
        recordA.setLosses(0);
        
        double index = recordA.index(100);
        assertTrue(index > 1.0, "All wins should produce high index");
    }
}
