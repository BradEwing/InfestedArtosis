package learning;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Jacksonized
@Builder
@Data
public class OpponentRecord {
    private String name;
    private String race;

    private int wins;
    private int losses;

    @Deprecated
    private Map<String, OpenerRecord> opponentStrategies;

    private Map<String, OpenerRecord> openerRecord;
}
