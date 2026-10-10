package learning;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomOpponentOpenerSelectionTest {

    private static final String NINE_POOL_GAS_HATCH = "9PoolGasHatchSpeed8D";

    @Test
    void anOpenerWithNoUnknownRacePriorRowIsPickedBeforeTheSeededOnes() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Unknown);
        OpponentRecord record = OpponentRecord.builder()
                .name("Random")
                .race("Unknown")
                .openerRecord(new HashMap<>())
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
        for (String opener : factory.getOpenerNames()) {
            record.getOpenerRecord().put(opener, Record.builder().opener(opener).build());
        }
        RacePrior prior = RacePrior.load();
        assertTrue(prior.arms(RacePrior.raceKey(Race.Unknown), RacePrior.KIND_OPENER).stream()
                .noneMatch(arm -> arm.name().equals(NINE_POOL_GAS_HATCH)));
        prior.seedIfNew(true, 0, record, new LearningRecordAccumulator("Random", Race.Unknown),
                RacePrior.raceKey(Race.Unknown));

        String selected = OpenerSelectionPolicy.select(null, factory, record, "", false, null, "Python");

        assertEquals(NINE_POOL_GAS_HATCH, selected);
    }
}
