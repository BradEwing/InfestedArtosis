package learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        double index = recordA.index(100, ownClock(recordA));
        assertEquals(UCBSelectionPolicy.CURIOSITY_CAP, index, 0.0000000001,
            "An empty map record should score the full curiosity bonus and nothing more");
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

        double index = mapRecord.index(100, ownClock(mapRecord));
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

        double index = opponentRecord.index(100, ownClock(opponentRecord));
        assertTrue(index > 0.0, "Opponent-specific record should have positive index");
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

        double recentIndex = recentWins.index(100, ownClock(recentWins));
        double oldIndex = oldWins.index(100, ownClock(oldWins));

        assertTrue(recentIndex > oldIndex,
            "Recent wins should score higher than old wins");
    }

    @Test
    void testZeroTotalGames() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);

        double index = recordA.index(0, ownClock(recordA));
        assertTrue(index >= 0.0 && index <= 1.0, "Zero totalGames should return random value between 0 and 1");
    }

    @Test
    void testSingleGame() {
        recordA.addWinTimestamp(baseTime);

        double index = recordA.index(100, ownClock(recordA));
        assertTrue(index >= 1.0, "Single win should have positive index");
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
     * A map record scores the same however many games the opponent has been played, so map
     * evidence never wins the blend on lifetime length alone.
     */
    @Test
    void testIndexIsInvariantInLifetimeLength() {
        recordA.addWinTimestamp(baseTime);
        recordA.addLossTimestamp(baseTime + 1000);
        recordA.setWins(1);
        recordA.setLosses(1);

        double atFiftyThree = recordA.index(53, ownClock(recordA));

        assertEquals(atFiftyThree, recordA.index(339, ownClock(recordA)), 0.0000000001,
            "The map index should not grow with the opponent's lifetime");
        assertEquals(atFiftyThree, recordA.index(1324, ownClock(recordA)), 0.0000000001,
            "The map index should not grow with the opponent's lifetime");
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
     * A map arm that lost the most recent game must score below an identical arm whose
     * loss is twenty games stale, so map-specific selection also pivots away from a
     * just-beaten strategy.
     */
    @Test
    void testLastGameLossRanksBelowStaleLossRecord() {
        MapAwareRecord freshLoss = MapAwareRecord.builder()
                .strategy("FreshLoss").mapName("MapA").wins(4).losses(1).build();
        MapAwareRecord staleLoss = MapAwareRecord.builder()
                .strategy("StaleLoss").mapName("MapA").wins(4).losses(1).build();
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
                "A loss in the last game should rank the map arm below a stale-loss twin");
    }

    @Test
    void testIdleArmRecoversRelativeToSelectedLeader() {
        MapAwareRecord idle = MapAwareRecord.builder()
                .strategy("Idle").mapName("MapA").wins(0).losses(1).build();
        MapAwareRecord leader = MapAwareRecord.builder()
                .strategy("Leader").mapName("MapA").wins(14).losses(0).build();
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
            assertTrue(gap > previousGap, "The idle map arm gap should rise after every selected-leader game");
            previousGap = gap;
        }
    }

    /** Map arms with equal means and different discounted evidence must separate, not tie. */
    @Test
    void testThinLosingMapArmsDoNotTie() {
        List<Long> gameTimestamps = new ArrayList<>();
        for (long timestamp = 1L; timestamp <= 40L; timestamp++) {
            gameTimestamps.add(timestamp);
        }
        MapAwareRecord heaviest = mapLossesAt(33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L);
        MapAwareRecord middling = mapLossesAt(30L, 31L);
        MapAwareRecord thinnest = mapLossesAt(20L);

        double heaviestIndex = heaviest.index(40, gameTimestamps);
        double middlingIndex = middling.index(40, gameTimestamps);
        double thinnestIndex = thinnest.index(40, gameTimestamps);

        assertTrue(heaviestIndex < middlingIndex,
                "Map arms with equal means and different evidence must not tie, measured "
                        + heaviestIndex + " and " + middlingIndex);
        assertTrue(middlingIndex < thinnestIndex,
                "Map arms with equal means and different evidence must not tie, measured "
                        + middlingIndex + " and " + thinnestIndex);
    }

    /**
     * The displacement guarantee on the map clock: a zero-mean map arm cannot outrank one whose
     * discounted map win rate clears the curiosity cap, at any history length.
     */
    @Test
    void testZeroMeanMapArmNeverOutranksAnIncumbentAboveTheCuriosityCap() {
        for (int historyLength : new int[] {30, 53, 339, 1324}) {
            List<Long> gameTimestamps = new ArrayList<>();
            MapAwareRecord incumbent = MapAwareRecord.builder()
                    .strategy("Incumbent").mapName("MapA").build();
            for (long timestamp = 1L; timestamp <= historyLength; timestamp++) {
                gameTimestamps.add(timestamp);
                if (timestamp % 10 == 3 || timestamp % 10 == 6 || timestamp % 10 == 9) {
                    incumbent.addWinTimestamp(timestamp);
                    incumbent.setWins(incumbent.getWins() + 1);
                } else {
                    incumbent.addLossTimestamp(timestamp);
                    incumbent.setLosses(incumbent.getLosses() + 1);
                }
            }
            double incumbentIndex = incumbent.index(historyLength, gameTimestamps);
            assertTrue(incumbentIndex > UCBSelectionPolicy.CURIOSITY_CAP,
                    "The fixture incumbent should lead by more than the cap at " + historyLength);

            for (int gamesSincePlayed : new int[] {0, 1, 10, 50, historyLength}) {
                MapAwareRecord challenger = MapAwareRecord.builder()
                        .strategy("Challenger").mapName("MapA").build();
                if (gamesSincePlayed > 0) {
                    challenger.addLossTimestamp(historyLength - gamesSincePlayed + 1L);
                    challenger.setLosses(1);
                }
                assertTrue(challenger.index(historyLength, gameTimestamps) < incumbentIndex,
                        "A zero-mean map arm dormant for " + gamesSincePlayed + " games displaced "
                                + "the incumbent at a lifetime of " + historyLength);
            }
        }
    }

    /** IA-269 deliberately guarantees relative recovery, not eventual overtake. */
    @Test
    void testDormantArmDoesNotOvertakePerfectLeader() {
        MapAwareRecord idle = MapAwareRecord.builder()
                .strategy("Idle").mapName("MapA").wins(0).losses(1).build();
        MapAwareRecord leader = MapAwareRecord.builder()
                .strategy("Leader").mapName("MapA").wins(500).losses(0).build();
        idle.addLossTimestamp(1L);
        List<Long> gameTimestamps = new ArrayList<>();
        gameTimestamps.add(1L);
        for (long timestamp = 2; timestamp <= 501; timestamp++) {
            leader.addWinTimestamp(timestamp);
            gameTimestamps.add(timestamp);
        }

        assertTrue(idle.index(500, gameTimestamps) < leader.index(500, gameTimestamps),
                "A dormant losing map arm should not overtake a perfect leader");
    }

    @Test
    void testWinningStrategyRanksFirstWithTomasCereHistory() {
        String history = "NNNNNNNNNNNNNNNNNNNNppOoOOoFHFHFhFhFttFpFhFtFOFnFOOFOFoFhFoFnFTFtFNFNFnFpFoFNFNFn"
                + "FtFHFhFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFNFnFNFNFNFn";
        List<Long> gameTimestamps = new ArrayList<>();
        MapAwareRecord fourPool = recordFromHistory('F', history, gameTimestamps);

        for (char strategy : new char[] {'N', 'P', 'O', 'H', 'T'}) {
            assertTrue(fourPool.index(history.length(), gameTimestamps)
                    > recordFromHistory(strategy, history, new ArrayList<>()).index(history.length(), gameTimestamps),
                    "4Pool should rank ahead of " + strategy);
        }
    }

    private MapAwareRecord mapLossesAt(long... timestamps) {
        MapAwareRecord record = MapAwareRecord.builder().strategy("Thin").mapName("MapA").build();
        for (long timestamp : timestamps) {
            record.addLossTimestamp(timestamp);
            record.setLosses(record.getLosses() + 1);
        }
        return record;
    }

    private MapAwareRecord recordFromHistory(char strategy, String history, List<Long> gameTimestamps) {
        MapAwareRecord record = MapAwareRecord.builder().strategy(String.valueOf(strategy)).mapName("MapA").build();
        for (int i = 0; i < history.length(); i++) {
            char result = history.charAt(i);
            long timestamp = i + 1L;
            gameTimestamps.add(timestamp);
            if (Character.toUpperCase(result) == strategy) {
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

    /**
     * The clock these cases were written against: the record's own games. Production instead ages a
     * map record on that map's games, so a test using this helper is exercising decay mechanics, not
     * the production clock.
     */
    private static List<Long> ownClock(MapAwareRecord record) {
        List<Long> clock = new ArrayList<>();
        clock.addAll(record.getWinTimestamps());
        clock.addAll(record.getLossTimestamps());
        return clock;
    }
}
