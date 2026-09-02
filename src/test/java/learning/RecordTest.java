package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
     * With 3 wins and the curiosity bonus, the index should be greater than 1.0.
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
        assertTrue(index > 1.0, "Index should be greater than 1.0 due to the curiosity bonus");
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

    /**
     * The index carries no dependence on how many games the opponent has been played, so a
     * strategy scores the same on its 53rd game as on its 1324th.
     */
    @Test
    void testIndexIsInvariantInLifetimeLength() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.setWins(1);
        recordA.setLosses(1);

        double atFiftyThree = recordA.index(53, ownClock(recordA));

        assertEquals(atFiftyThree, recordA.index(339, ownClock(recordA)), 0.0000000001,
            "The index should not grow with the opponent's lifetime");
        assertEquals(atFiftyThree, recordA.index(1324, ownClock(recordA)), 0.0000000001,
            "The index should not grow with the opponent's lifetime");
    }

    /**
     * Tests the gamma decay constant (0.95) is applied correctly in the D-UCB algorithm.
     * With 3 wins and GAMMA = 0.95:
     * - discountedWins = 1.0 + 0.95 + 0.95^2 = 2.8525
     * - discountedGames = 1.0 + 0.95 + 0.95^2 = 2.8525
     * - sampleMean = 2.8525 / 2.8525 = 1.0
     * - curiosity = 0.15 * (1 - 2.8525 / 10) ≈ 0.107
     * The index should be greater than 1.0 due to the curiosity bonus.
     */
    @Test
    void testGammaConstant() {
        recordA.addWinTimestamp(baseTime);
        recordA.addWinTimestamp(baseTime + 1000);
        recordA.addWinTimestamp(baseTime + 2000);
        recordA.setWins(3);
        recordA.setLosses(0);

        double index = recordA.index(100, ownClock(recordA));

        assertTrue(index > 1.0, "Index should be greater than 1.0 due to the curiosity bonus");
        assertTrue(index < 3.0, "Index should be reasonable");
    }

    /**
     * One discount: a twenty-game-old loss carries exactly the weight a win of the same age
     * carries, so the discounted mean stays a win rate.
     */
    @Test
    void testWinsAndLossesDecayAtTheSameRate() {
        Record record = Record.builder().opener("12Hatch").wins(4).losses(1).build();
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1L; timestamp <= 25L; timestamp++) {
            gameTimestamps.add(timestamp);
            if (timestamp <= 4L) {
                record.addWinTimestamp(timestamp);
            }
        }
        record.addLossTimestamp(5L);

        double discountedWins = Math.pow(UCBSelectionPolicy.GAMMA, 24)
                + Math.pow(UCBSelectionPolicy.GAMMA, 23)
                + Math.pow(UCBSelectionPolicy.GAMMA, 22)
                + Math.pow(UCBSelectionPolicy.GAMMA, 21);
        double symmetricMean = discountedWins
                / (discountedWins + Math.pow(UCBSelectionPolicy.GAMMA, 20));

        assertEquals(symmetricMean, record.discountedMean(gameTimestamps), 0.0000000001,
                "The loss should be weighted by the same gamma as the wins");
    }

    /**
     * The win-rate gates elsewhere read this mean in win-rate points, so a steady 30% history
     * has to report about 30%. The split discount reported roughly 0.15 here.
     */
    @Test
    void testThirtyGamesAtThirtyPercentReadsAsThirtyPercent() {
        List<Long> gameTimestamps = new ArrayList<>();
        Record record = evenlySpacedThirtyPercentRecord(30, gameTimestamps);

        double mean = record.discountedMean(gameTimestamps);

        assertEquals(9, record.wins(), "The fixture should be exactly 30% of 30 games");
        assertEquals(30, record.games(), "The fixture should be exactly 30 games");
        assertTrue(mean >= 0.27 && mean <= 0.33,
                "A steady 30% history should read near 0.30, measured " + mean);
    }

    /**
     * The displacement guarantee: an arm with a zero discounted mean cannot outrank an incumbent
     * whose discounted mean clears the curiosity cap, however long the history and however
     * dormant the challenger.
     */
    @Test
    void testZeroMeanArmNeverOutranksAnIncumbentAboveTheCuriosityCap() {
        for (int historyLength : new int[] {30, 53, 339, 1324}) {
            List<Long> gameTimestamps = new ArrayList<>();
            Record incumbent = evenlySpacedThirtyPercentRecord(historyLength, gameTimestamps);
            double incumbentIndex = incumbent.index(historyLength, gameTimestamps);
            assertTrue(incumbent.discountedMean(gameTimestamps) > UCBSelectionPolicy.CURIOSITY_CAP,
                    "The fixture incumbent should lead by more than the cap at " + historyLength);

            for (int gamesSincePlayed : new int[] {0, 1, 10, 50, historyLength}) {
                Record challenger = Record.builder().opener("Challenger").build();
                if (gamesSincePlayed > 0) {
                    challenger.addLossTimestamp(historyLength - gamesSincePlayed + 1L);
                    challenger.setLosses(1);
                }
                assertTrue(challenger.index(historyLength, gameTimestamps) < incumbentIndex,
                        "A zero-mean arm dormant for " + gamesSincePlayed + " games displaced the "
                                + "incumbent at a lifetime of " + historyLength);
            }
        }
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
        assertEquals(UCBSelectionPolicy.CURIOSITY_CAP, index, 0.0000000001,
            "An unplayed strategy should score the full curiosity bonus and nothing more");
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
        assertTrue(index > 0.0, "All losses should still produce positive index due to curiosity");
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

    /**
     * Three arms with no discounted wins and different discounted evidence must separate. The
     * old floor tied them, which made the pick among them a coin flip.
     */
    @Test
    void testThinLosingArmsDoNotTie() {
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1L; timestamp <= 40L; timestamp++) {
            gameTimestamps.add(timestamp);
        }
        Record heaviest = lossesAt(33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L);
        Record middling = lossesAt(30L, 31L);
        Record thinnest = lossesAt(20L);

        double heaviestIndex = heaviest.index(40, gameTimestamps);
        double middlingIndex = middling.index(40, gameTimestamps);
        double thinnestIndex = thinnest.index(40, gameTimestamps);

        assertTrue(heaviestIndex < middlingIndex,
                "Arms with equal means and different evidence must not tie, measured "
                        + heaviestIndex + " and " + middlingIndex);
        assertTrue(middlingIndex < thinnestIndex,
                "Arms with equal means and different evidence must not tie, measured "
                        + middlingIndex + " and " + thinnestIndex);
    }

    @Test
    void testCuriosityIsBoundedAndFadesWithEvidence() {
        double previous = Double.MAX_VALUE;
        for (double discountedGames : new double[] {0.0, 1.0, 5.0, 10.0, 20.0, 50.0}) {
            double curiosity = UCBSelectionPolicy.curiosity(discountedGames);
            assertTrue(curiosity >= 0.0 && curiosity <= UCBSelectionPolicy.CURIOSITY_CAP,
                    "Curiosity should stay within the cap, measured " + curiosity);
            assertTrue(curiosity <= previous, "Curiosity should never grow with evidence");
            previous = curiosity;
        }
        assertEquals(UCBSelectionPolicy.CURIOSITY_CAP, UCBSelectionPolicy.curiosity(0.0), 0.0000000001,
                "An arm with no evidence should earn the full bonus");
        assertEquals(0.0, UCBSelectionPolicy.curiosity(UCBSelectionPolicy.CURIOSITY_HORIZON), 0.0000000001,
                "The bonus should reach zero at the horizon");
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

    private Record lossesAt(long... timestamps) {
        Record record = Record.builder().opener("Thin").build();
        for (long timestamp : timestamps) {
            record.addLossTimestamp(timestamp);
            record.setLosses(record.getLosses() + 1);
        }
        return record;
    }

    /**
     * A record that wins three of every ten games, spaced evenly, and the matching game clock.
     * The most recent game is a loss, so the fixture does not flatter the discounted mean.
     */
    private Record evenlySpacedThirtyPercentRecord(int games, List<Long> gameTimestamps) {
        Record record = Record.builder().opener("Incumbent").build();
        for (long timestamp = 1L; timestamp <= games; timestamp++) {
            gameTimestamps.add(timestamp);
            if (timestamp % 10 == 3 || timestamp % 10 == 6 || timestamp % 10 == 9) {
                record.addWinTimestamp(timestamp);
                record.setWins(record.getWins() + 1);
            } else {
                record.addLossTimestamp(timestamp);
                record.setLosses(record.getLosses() + 1);
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
