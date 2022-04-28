package learning;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Builder
@Data
public class OpponentRecord {
    private String name;
    private String race;
    private Map<String, StrategyRecord> opponentStrategies = new HashMap();
}
