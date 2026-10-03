package unit.scout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecallFateTest {

    private static final int RECALL = 5000;

    @Test
    void aRecalledScoutThatIsGoneIsDeadWhateverTheFrame() {
        assertEquals(BaseCheckScheduler.RecallFate.DIED, BaseCheckScheduler.recallFate(false, RECALL, RECALL + 2));
        assertEquals(BaseCheckScheduler.RecallFate.DIED,
                BaseCheckScheduler.recallFate(false, RECALL, RECALL + BaseCheckScheduler.RECALL_DEATH_WINDOW_FRAMES));
    }

    @Test
    void aLivingRecalledScoutStaysPendingInsideTheWindow() {
        assertEquals(BaseCheckScheduler.RecallFate.PENDING, BaseCheckScheduler.recallFate(true, RECALL, RECALL));
        assertEquals(BaseCheckScheduler.RecallFate.PENDING, BaseCheckScheduler.recallFate(true, RECALL,
                RECALL + BaseCheckScheduler.RECALL_DEATH_WINDOW_FRAMES - 1));
    }

    @Test
    void aRecalledScoutAliveAfterTheWindowSurvived() {
        assertEquals(BaseCheckScheduler.RecallFate.SURVIVED, BaseCheckScheduler.recallFate(true, RECALL,
                RECALL + BaseCheckScheduler.RECALL_DEATH_WINDOW_FRAMES));
    }
}
