package learning;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningHistoryTest {

    @Test
    void theLastGamesDetectedStrategiesComeOldestFirst() {
        LearningHistory history = new LearningHistory(Arrays.asList(game("A"), game("B"), game("C"), game("D")));

        assertEquals(Arrays.asList("B", "C", "D"), history.lastGamesDetectedStrategies(3));
    }

    @Test
    void fewerGamesThanAskedForGiveAllOfThem() {
        assertEquals(Collections.singletonList("A"),
                new LearningHistory(Collections.singletonList(game("A"))).lastGamesDetectedStrategies(3));
        assertEquals(Collections.emptyList(), new LearningHistory(new ArrayList<>()).lastGamesDetectedStrategies(3));
    }

    private static GameRecord game(String detectedStrategies) {
        return GameRecord.builder().detectedStrategies(detectedStrategies).build();
    }
}
