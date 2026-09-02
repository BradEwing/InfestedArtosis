package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        double index = recordA.index(0, ownClock(recordA));
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

        double indexA = recordA.index(6, ownClock(recordA));
        double indexB = recordB.index(6, ownClock(recordB));

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

        double index = record.index(100, ownClock(record));
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

        double index = recordA.index(100, ownClock(recordA));

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

        double recentIndex = recentWins.index(100, ownClock(recentWins));
        double oldIndex = oldWins.index(100, ownClock(oldWins));

        assertTrue(recentIndex > oldIndex,
            "Recent wins should score higher than old wins");
    }

    @Test
    void testZeroTotalGames() {
        recordA.addWinTimestamp(baseTime);

        double index = recordA.index(0, ownClock(recordA));
        assertTrue(index >= 0.0 && index <= 1.0, "Zero totalGames should return random value between 0 and 1");
    }

    @Test
    void testSingleGame() {
        recordA.addWinTimestamp(baseTime);
        recordA.setWins(1);
        recordA.setLosses(0);

        double index = recordA.index(1, ownClock(recordA));
        assertTrue(index > 0.0, "Single win should have positive index");
    }

    @Test
    void testDiscountedWinsCalculation() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.addWinTimestamp(baseTime + 2000);

        double index = recordA.index(100, ownClock(recordA));

        assertTrue(index > 0.5, "Recent wins should give positive index");
    }

    @Test
    void testExplorationTerm() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.setWins(1);
        recordA.setLosses(1);

        double indexLow = recordA.index(10, ownClock(recordA));
        double indexHigh = recordA.index(1000, ownClock(recordA));

        assertTrue(indexHigh > indexLow,
            "Higher totalGames should increase exploration term");
    }

    /**
     * Tests the gamma decay constant (0.95) is applied correctly in the D-UCB algorithm.
     * With 3 wins and GAMMA_WIN = 0.95:
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

        double index = recordA.index(100, ownClock(recordA));

        assertTrue(index > 1.0, "Index should be greater than 1.0 due to exploration");
        assertTrue(index < 3.0, "Index should be reasonable");
    }

    /**
     * Asymmetric decay: twenty games after a loss, the loss still drags the discounted
     * win rate below what symmetric decay would give. The four wins fade at GAMMA_WIN
     * while the loss is weighted by GAMMA_LOSS^20 instead of GAMMA_WIN^20.
     */
    @Test
    void testLossStaysHeavyTwentyGamesLater() {
        Record record = Record.builder().opener("12Hatch").wins(4).losses(1).build();
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1L; timestamp <= 25L; timestamp++) {
            gameTimestamps.add(timestamp);
            if (timestamp <= 4L) {
                record.addWinTimestamp(timestamp);
            }
        }
        record.addLossTimestamp(5L);

        double mean = record.discountedMean(gameTimestamps);
        double discountedWins = Math.pow(UCBSelectionPolicy.GAMMA_WIN, 24)
                + Math.pow(UCBSelectionPolicy.GAMMA_WIN, 23)
                + Math.pow(UCBSelectionPolicy.GAMMA_WIN, 22)
                + Math.pow(UCBSelectionPolicy.GAMMA_WIN, 21);
        double asymmetricMean = discountedWins
                / (discountedWins + Math.pow(UCBSelectionPolicy.GAMMA_LOSS, 20));
        double symmetricMean = discountedWins
                / (discountedWins + Math.pow(UCBSelectionPolicy.GAMMA_WIN, 20));

        assertEquals(asymmetricMean, mean, 0.0000000001, "The loss weight should use GAMMA_LOSS");
        assertTrue(mean < symmetricMean, "The loss should decay slower than the wins");
    }

    /**
     * An arm that lost the most recent game must score below an identical arm whose loss
     * is twenty games stale, so the bandit pivots away from a just-beaten opener.
     */
    @Test
    void testLastGameLossRanksBelowStaleLossRecord() {
        Record freshLoss = Record.builder().opener("FreshLoss").wins(4).losses(1).build();
        Record staleLoss = Record.builder().opener("StaleLoss").wins(4).losses(1).build();
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1L; timestamp <= 25L; timestamp++) {
            gameTimestamps.add(timestamp);
        }
        for (long timestamp = 1L; timestamp <= 4L; timestamp++) {
            freshLoss.addWinTimestamp(timestamp);
            staleLoss.addWinTimestamp(timestamp);
        }
        staleLoss.addLossTimestamp(5L);
        freshLoss.addLossTimestamp(25L);

        assertTrue(freshLoss.index(25, gameTimestamps) < staleLoss.index(25, gameTimestamps),
                "A loss in the last game should rank the arm below a stale-loss twin");
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
        double index = recordA.index(0, ownClock(recordA));
        assertTrue(index >= 0.0 && index <= 1.0, "Empty record with zero totalGames should return random value between 0 and 1");
    }

    @Test
    void testRecordWithZeroGames() {
        recordA.setWins(0);
        recordA.setLosses(0);
        double index = recordA.index(100, ownClock(recordA));
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

        double index = recordA.index(100, ownClock(recordA));
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

        double index = recordA.index(100, ownClock(recordA));
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

        double index = recordA.index(100, ownClock(recordA));
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

        double index = recordA.index(100, ownClock(recordA));
        assertTrue(index > 1.0, "All wins should produce high index");
    }

    @Test
    void testIdleArmRecoversRelativeToSelectedLeader() {
        Record idle = Record.builder().opener("Idle").wins(0).losses(1).build();
        Record leader = Record.builder().opener("Leader").wins(14).losses(0).build();
        idle.addLossTimestamp(1L);
        List<Long> gameTimestamps = new ArrayList<>();
        gameTimestamps.add(1L);
        for (long timestamp = 2; timestamp <= 15; timestamp++) {
            leader.addWinTimestamp(timestamp);
            gameTimestamps.add(timestamp);
        }
        double previousGap = idle.index(15, gameTimestamps) - leader.index(15, gameTimestamps);

        for (long timestamp = 16; timestamp <= 30; timestamp++) {
            gameTimestamps.add(timestamp);
            leader.addWinTimestamp(timestamp);
            leader.setWins(leader.getWins() + 1);
            double gap = idle.index((int) timestamp, gameTimestamps)
                    - leader.index((int) timestamp, gameTimestamps);
            assertTrue(gap > previousGap, "The idle arm gap should rise after every selected-leader game");
            previousGap = gap;
        }
    }

    @Test
    void testIdleArmGapIsFrozenWhileBothArmsAreFloored() {
        Record idle = Record.builder().opener("Idle").wins(0).losses(1).build();
        Record leader = Record.builder().opener("Leader").wins(1).losses(0).build();
        idle.addLossTimestamp(1L);
        leader.addWinTimestamp(2L);
        List<Long> gameTimestamps = new ArrayList<>(Arrays.asList(1L, 2L));
        double initialGap = idle.index(2, gameTimestamps) - leader.index(2, gameTimestamps);

        for (long timestamp = 3; timestamp <= 8; timestamp++) {
            gameTimestamps.add(timestamp);
            leader.addWinTimestamp(timestamp);
            leader.setWins(leader.getWins() + 1);
            double gap = idle.index((int) timestamp, gameTimestamps)
                    - leader.index((int) timestamp, gameTimestamps);
            assertEquals(initialGap, gap, 0.0000000001, "The gap should stay frozen while both arms are floored");
        }
    }

    @Test
    void testExplorationBonusIsBoundedAtRepresentativeCounts() {
        double[] discountedGames = {0.0000001, 1.0, 5.0, 10.0};
        int[] totalGames = {153, 10000};
        double[] bonusBounds = {1.01, 1.36};
        for (int i = 0; i < totalGames.length; i++) {
            for (double discounted : discountedGames) {
                double sampleMean = 0.4;
                double index = sampleMean + UCBSelectionPolicy.explorationTerm(totalGames[i], discounted);
                assertTrue(index - sampleMean <= bonusBounds[i],
                        "The exploration bonus should stay within the floor-derived bound");
            }
        }
    }

    /** IA-269 deliberately guarantees relative recovery, not eventual overtake. */
    @Test
    void testDormantArmDoesNotOvertakePerfectLeader() {
        Record idle = Record.builder().opener("Idle").wins(0).losses(1).build();
        Record leader = Record.builder().opener("Leader").wins(500).losses(0).build();
        idle.addLossTimestamp(1L);
        List<Long> gameTimestamps = new ArrayList<>();
        gameTimestamps.add(1L);
        for (long timestamp = 2; timestamp <= 501; timestamp++) {
            leader.addWinTimestamp(timestamp);
            gameTimestamps.add(timestamp);
        }

        assertTrue(idle.index(500, gameTimestamps) < leader.index(500, gameTimestamps),
                "A dormant losing arm should not overtake a perfect leader");
    }

    @Test
    void testWinningOpenerRanksFirstWithTomasCereHistory() {
        String history = "NNNNNNNNNNNNNNNNNNNNppOoOOoFHFHFhFhFttFpFhFtFOFnFOOFOFoFhFoFnFTFtFNFNFnFpFoFNFNFn"
                + "FtFHFhFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFnFNFNFNFn";
        List<Long> gameTimestamps = new ArrayList<>();
        Record fourPool = recordFromHistory('F', history, gameTimestamps);

        for (char opener : new char[] {'N', 'P', 'O', 'H', 'T'}) {
            assertTrue(fourPool.index(history.length(), gameTimestamps)
                    > recordFromHistory(opener, history, new ArrayList<>()).index(history.length(), gameTimestamps),
                    "4Pool should rank ahead of " + opener);
        }
    }

    private Record recordFromHistory(char opener, String history, List<Long> gameTimestamps) {
        Record record = Record.builder().opener(String.valueOf(opener)).build();
        for (int i = 0; i < history.length(); i++) {
            char result = history.charAt(i);
            long timestamp = i + 1L;
            gameTimestamps.add(timestamp);
            if (Character.toUpperCase(result) == opener) {
                if (Character.isUpperCase(result)) {
                    record.addWinTimestamp(timestamp);
                    record.setWins(record.getWins() + 1);
                } else {
                    record.addLossTimestamp(timestamp);
                    record.setLosses(record.getLosses() + 1);
                }
            }
        }
        return record;
    }

    /** The clock these cases were written against: the record's own games. */
    private static List<Long> ownClock(Record record) {
        List<Long> clock = new ArrayList<>();
        clock.addAll(record.getWinTimestamps());
        clock.addAll(record.getLossTimestamps());
        return clock;
    }
}
